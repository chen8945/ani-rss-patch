package ani.rss.notification;

import ani.rss.commons.NumberFormatUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.entity.NotificationConfig;
import ani.rss.enums.NotificationStatusEnum;
import ani.rss.enums.StringEnum;
import ani.rss.service.DownloadService;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.ItemsUtil;
import ani.rss.util.other.RenameUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Opt;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import wushuo.tmdb.api.entity.Tmdb;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface BaseNotification {

    /**
     * 测试
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     */
    void test(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum);

    /**
     * 发送通知
     *
     * @param notificationConfig     通知配置
     * @param ani                    订阅
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 是否成功
     */
    Boolean send(NotificationConfig notificationConfig, Ani ani, String text, NotificationStatusEnum notificationStatusEnum);

    /**
     * 替换通知模版
     *
     * @param ani                    订阅
     * @param notificationConfig     通知设置
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 替换后的通知模版
     */
    default String replaceNotificationTemplate(Ani ani, NotificationConfig notificationConfig, String text, NotificationStatusEnum notificationStatusEnum) {
        String notificationTemplate = notificationConfig.getNotificationTemplate();

        String comment = Opt.ofNullable(notificationConfig)
                .map(NotificationConfig::getComment)
                .filter(StrUtil::isNotBlank)
                .orElse("无备注");

        notificationTemplate = notificationTemplate.replace("${comment}", comment);

        return replaceNotificationTemplate(ani, notificationTemplate, text, notificationStatusEnum);
    }

    /**
     * 替换通知模版
     *
     * @param ani                    订阅
     * @param notificationTemplate   通知模版
     * @param text                   通知内容
     * @param notificationStatusEnum 通知状态
     * @return 替换后的通知模版
     */
    default String replaceNotificationTemplate(Ani ani, String notificationTemplate, String text, NotificationStatusEnum notificationStatusEnum) {
        notificationTemplate = notificationTemplate.replace("${text}", text);

        // 集数
        double episode = 1.0;
        if (ReUtil.contains(StringEnum.SEASON_REG, text)) {
            episode = Double.parseDouble(ReUtil.get(StringEnum.SEASON_REG, text, 2));
        }

        String episodeFormat = String.format("%02d", (int) episode);

        // x.5
        if (ItemsUtil.is5(episode)) {
            episodeFormat = episodeFormat + ".5";
        }

        notificationTemplate = notificationTemplate.replace("${episode}",
                NumberFormatUtils.format(episode, 1, 0)
        );
        notificationTemplate = notificationTemplate.replace("${episodeFormat}", episodeFormat);


        Date releaseDate = ani.getReleaseDate();
        int year = DateUtil.year(releaseDate);
        int month = DateUtil.month(releaseDate) + 1;
        int date = DateUtil.dayOfMonth(releaseDate);

        notificationTemplate = notificationTemplate.replace("${year}", String.valueOf(year));
        notificationTemplate = notificationTemplate.replace("${month}", String.valueOf(month));
        notificationTemplate = notificationTemplate.replace("${date}", String.valueOf(date));

        // 通知标题仅剥离 TMDB ID，保留年份；不改订阅名称和实际下载路径。
        notificationTemplate = notificationTemplate.replace("${title}",
                ReUtil.replaceAll(StrUtil.nullToEmpty(ani.getTitle()), StringEnum.TMDB_ID_REG, ""));
        notificationTemplate = notificationTemplate.replace("${themoviedbName}",
                ReUtil.replaceAll(StrUtil.nullToEmpty(ani.getThemoviedbName()), StringEnum.TMDB_ID_REG, ""));

        List<Func1<Ani, Object>> list = List.of(
                Ani::getScore,
                Ani::getSeason,
                Ani::getBgmUrl,
                Ani::getCurrentEpisodeNumber,
                Ani::getTotalEpisodeNumber,
                Ani::getSubgroup
        );

        int season = ani.getSeason();
        String seasonFormat = String.format("%02d", season);
        notificationTemplate = notificationTemplate.replace("${seasonFormat}", seasonFormat);

        notificationTemplate = RenameUtil.replaceField(notificationTemplate, ani, list);

        String tmdbId = Optional.of(ani)
                .map(Ani::getTmdb)
                .map(Tmdb::getId)
                .filter(StrUtil::isNotBlank)
                .orElse("");
        notificationTemplate = notificationTemplate.replace("${tmdbid}", tmdbId);

        // Bangumi ID 仅从已保存的条目链接提取，缺失时留空，不额外请求网络。
        String bgmId = ReUtil.get("(?i)^https?://[^/?#\\s]+/subject/([0-9]+)/?(?:[?#].*)?$",
                StrUtil.nullToEmpty(ani.getBgmUrl()).trim(), 1);
        notificationTemplate = notificationTemplate.replace("${bgmid}", StrUtil.nullToEmpty(bgmId));

        String tmdbUrl = "";
        if (StrUtil.isNotBlank(tmdbId)) {
            Boolean ova = Opt.ofNullable(ani)
                    .map(Ani::getOva)
                    .orElse(false);
            String type = ova ? "movie" : "tv";
            tmdbUrl = StrFormatter.format("https://www.themoviedb.org/{}/{}", type, tmdbId);
        }
        notificationTemplate = notificationTemplate.replace("${tmdburl}", tmdbUrl);

        String emoji = notificationStatusEnum.getEmoji();
        String action = notificationStatusEnum.getAction();

        notificationTemplate = notificationTemplate.replace("${emoji}", emoji);
        notificationTemplate = notificationTemplate.replace("${action}", action);

        DownloadService downloadService = SpringUtil.getBean(DownloadService.class);
        String downloadPath = downloadService.getDownloadPath(ani);
        notificationTemplate = notificationTemplate.replace("${downloadPath}", downloadPath);

        if (notificationTemplate.contains("${jpTitle}")) {
            String jpTitle = RenameUtil.getJpTitle(ani);
            notificationTemplate = notificationTemplate.replace("${jpTitle}", jpTitle);
        }

        notificationTemplate = RenameUtil.replaceEpisodeTitle(notificationTemplate, episode, ani);

        if (!notificationTemplate.contains("${notification}")) {
            return notificationTemplate.trim();
        }

        Config config = ConfigUtil.CONFIG;
        String template = config.getNotificationTemplate();

        template = replaceNotificationTemplate(ani, template, text, notificationStatusEnum);

        notificationTemplate = notificationTemplate.replace("${notification}", template);

        return notificationTemplate.trim();
    }
}
