package ani.rss.service;

import ani.rss.commons.FileUtils;
import ani.rss.entity.Ani;
import ani.rss.entity.Config;
import ani.rss.enums.StringEnum;
import ani.rss.util.basic.HttpReq;
import ani.rss.util.other.BgmUtil;
import ani.rss.util.other.ConfigUtil;
import ani.rss.util.other.ItemsUtil;
import ani.rss.util.other.TmdbUtils;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import wushuo.tmdb.api.entity.*;
import wushuo.tmdb.api.enums.TmdbTypeEnum;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 刮削
 */
@Slf4j
@Service
public class ScrapeService {

    private static final Config CONFIG = ConfigUtil.CONFIG;

    @Resource
    private NfoGenerator nfoGenerator;

    @Lazy
    @Resource
    private DownloadService downloadService;

    /**
     * 刮削
     *
     * @param ani         订阅
     * @param forceScrape 强制刮削
     */
    public void scrape(Ani ani, Boolean forceScrape) {
        String title = ani.getTitle();

        Tmdb tmdb = ani.getTmdb();

        if (Objects.isNull(tmdb)) {
            return;
        }

        boolean isOva = ani.getOva();
        TmdbTypeEnum tmdbTypeEnum = isOva ? TmdbTypeEnum.MOVIE : TmdbTypeEnum.TV;
        try {
            log.info("正在刮削 ... {}", title);
            // 下载位置
            String downloadPath = downloadService.getDownloadPath(ani);
            // 更新tmdb信息
            Optional<Tmdb> tmdbOptional = TmdbUtils.getTmdb(tmdb, tmdbTypeEnum);
            if (tmdbOptional.isEmpty()) {
                log.warn("刮削失败: 获取tmdb失败 {}", tmdb.getId());
                return;
            }
            if (isOva) {
                scrapeMovie(tmdb, downloadPath, forceScrape);
            } else {
                int season = ani.getSeason();
                scrapeTv(tmdb, season, downloadPath, forceScrape);
            }
            saveBangumiIni(ani, forceScrape);
            log.info("刮削完成 {}", title);
        } catch (Exception e) {
            log.error("刮削错误 {}", title);
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 电影刮削
     *
     * @param tmdb         TMDB
     * @param downloadPath 保存位置
     * @param force        强制
     * @throws Exception 异常
     */
    public void scrapeMovie(Tmdb tmdb, String downloadPath, Boolean force) throws Exception {
        List<File> files = FileUtils.listFileList(downloadPath);

        if (files.isEmpty()) {
            return;
        }

        Optional<File> first = files
                .stream()
                .filter(file -> {
                    String extName = FileUtil.extName(file);
                    if (StrUtil.isBlank(extName)) {
                        return false;
                    }
                    return FileUtils.isVideoFormat(extName);
                })
                .max(Comparator.comparingLong(File::length));

        if (first.isEmpty()) {
            // 找不到视频文件
            return;
        }

        File file = first.get();
        String mainName = FileUtil.mainName(file);

        // 保存nfo
        File nfoFile = new File(downloadPath, mainName + ".nfo");
        if (force || !FileUtil.exist(nfoFile)) {
            nfoGenerator.generateMovieNfo(tmdb, nfoFile.toString());
        }

        saveTmdbImages(tmdb, downloadPath, force);
    }

    /**
     * 电视剧刮削
     *
     * @param tmdb         TMDB
     * @param season       季度
     * @param downloadPath 保存位置
     * @param force        强制
     * @throws Exception 异常
     */
    public void scrapeTv(Tmdb tmdb, Integer season, String downloadPath, Boolean force) throws Exception {
        // 下载位置
        File downloadPathFile = new File(downloadPath);
        if (!FileUtil.exist(downloadPathFile)) {
            return;
        }

        // tvshow.nfo
        File tvShowNfoFile = new File(downloadPathFile.getParent(), "tvshow.nfo");
        if (force || !FileUtil.exist(tvShowNfoFile)) {
            nfoGenerator.generateTvShowNfo(tmdb, tvShowNfoFile.toString());
        }

        saveTmdbImages(tmdb, downloadPathFile.getParent(), force);

        Optional<TmdbSeason> optional = TmdbUtils.getTmdbSeason(tmdb, season);
        if (optional.isEmpty()) {
            return;
        }

        String seasonFormat = String.format("%02d", season);

        TmdbSeason tmdbSeason = optional.get();

        // 季封面
        String seasonPosterPath = tmdbSeason.getPosterPath();
        seasonPosterPath = StrUtil.blankToDefault(seasonPosterPath, tmdb.getPosterPath());
        String seasonPosterExtName = FileUtil.extName(seasonPosterPath);
        File seasonPosterFile = new File(downloadPathFile.getParent(), "season" + seasonFormat + "-poster." + seasonPosterExtName);
        saveImages(seasonPosterPath, seasonPosterFile, force);

        // 季nfo
        File seasonNfoFile = new File(downloadPathFile, "season.nfo");
        if (force || !seasonNfoFile.exists()) {
            nfoGenerator.generateSeasonNfo(tmdbSeason, seasonNfoFile.toString());
        }

        List<File> files = FileUtils.listFileList(downloadPathFile);

        Map<Integer, TmdbEpisode> episodeMap = tmdbSeason
                .getEpisodes()
                .stream()
                .collect(Collectors.toMap(TmdbEpisode::getEpisodeNumber, it -> it));

        // 追更天数
        Integer followDay = CONFIG.getFollowDay();

        // 以下开始保存集的 thumb、nfo
        for (File file : files) {
            String extName = FileUtil.extName(file);
            if (StrUtil.isBlank(extName)) {
                // 扩展名为空
                continue;
            }

            if (!FileUtils.isVideoFormat(extName)) {
                // 非视频文件
                continue;
            }

            String mainName = FileUtil.mainName(file);
            if (!ReUtil.contains(StringEnum.SEASON_REG, mainName)) {
                // 命名不标准
                continue;
            }

            int seasonNumber = Integer.parseInt(ReUtil.get(StringEnum.SEASON_REG, mainName, 1));
            if (season != seasonNumber) {
                // 季对应不上 跳过
                continue;
            }

            Double episodeNumber =
                    Double.parseDouble(ReUtil.get(StringEnum.SEASON_REG, mainName, 2));

            if (ItemsUtil.is5(episodeNumber)) {
                continue;
            }

            if (!episodeMap.containsKey(episodeNumber.intValue())) {
                // 找不到对应集
                continue;
            }

            TmdbEpisode tmdbEpisode = episodeMap.get(episodeNumber.intValue());

            // 该集的播出日期
            Date airDate = Optional.of(tmdbEpisode)
                    .map(TmdbEpisode::getAirDate)
                    .orElse(new Date());

            // 最晚追更时间
            Date date = DateUtil.offsetDay(new Date(), -followDay);

            // 播出日期 >= 最晚追更时间 强制刷新元数据
            boolean isFollow = airDate.getTime() >= date.getTime();

            // thumb
            String thumbPath = tmdbEpisode.getStillPath();
            if (StrUtil.isNotBlank(thumbPath)) {
                String thumbExtName = FileUtil.extName(thumbPath);
                File thumbFile = new File(downloadPathFile, mainName + "-thumb." + thumbExtName);

                // 判断条件: 追更 or 强制
                saveImages(thumbPath, thumbFile, isFollow || force);
            }

            // nfo
            String episodeFile = downloadPathFile + "/" + mainName + ".nfo";
            // 判断条件: 追更 or 强制 or 元数据不存在
            if (isFollow || force || !FileUtil.exist(episodeFile)) {
                nfoGenerator.generateEpisodeNfo(tmdbEpisode, episodeFile);
            }
        }
    }

    public void saveTmdbImages(Tmdb tmdb, String outputPath, Boolean force) {
        String posterPath = tmdb.getPosterPath();
        String fanartPath = tmdb.getBackdropPath();

        String posterExtName = FileUtil.extName(posterPath);
        String fanartExtName = FileUtil.extName(fanartPath);

        File posterFile = new File(outputPath, "poster." + posterExtName);
        File fanartFile = new File(outputPath, "fanart." + fanartExtName);

        // 封面、背景图
        saveImages(posterPath, posterFile, force);
        saveImages(fanartPath, fanartFile, force);

        // 保存更多的背景图
        TmdbImages tmdbImages = TmdbUtils.getTmdbImages(tmdb, tmdb.getTmdbType());
        List<TmdbImage> backdrops = tmdbImages.getBackdrops();
        backdrops = backdrops
                .stream()
                .filter(tmdbImage -> {
                    Integer width = tmdbImage.getWidth();
                    String filePath = tmdbImage.getFilePath();
                    if (fanartPath.equals(filePath)) {
                        return false;
                    }
                    return width >= 1280;
                })
                .limit(4)
                .toList();
        for (int i = 0; i < backdrops.size(); i++) {
            TmdbImage tmdbImage = backdrops.get(i);
            String filePath = tmdbImage.getFilePath();
            String extName = FileUtil.extName(filePath);

            File file = new File(outputPath, StrUtil.format("fanart{}.{}", i + 1, extName));
            saveImages(filePath, file, force);
        }

        // 保存logo
        List<TmdbImage> logos = tmdbImages.getLogos();
        if (CollUtil.isNotEmpty(logos)) {
            TmdbImage tmdbImage = logos.get(0);
            String logoPath = tmdbImage.getFilePath();
            String extName = FileUtil.extName(logoPath);
            File logoFile = new File(outputPath, "clearlogo." + extName);
            saveImages(logoPath, logoFile, force);
        }
    }

    /**
     * 保存图片
     *
     * @param tmdbPath tmdb路径
     * @param saveFile 保存位置
     * @param force    强制
     */
    public void saveImages(String tmdbPath, File saveFile, Boolean force) {
        if (StrUtil.isBlank(tmdbPath)) {
            return;
        }

        if (!force) {
            if (saveFile.exists()) {
                return;
            }
        }

        FileUtil.del(saveFile);

        String tmdbImage = CONFIG.getTmdbImage();

        HttpReq.get(tmdbImage + "/t/p/original" + tmdbPath)
                .then(res -> {
                    try (InputStream inputStream = res.bodyStream()) {
                        FileUtil.writeFromStream(inputStream, saveFile, true);
                    } catch (Exception ignored) {
                    }
                });

        log.info("已保存图片 {}", saveFile);
    }

    /**
     * 季目录 bangumi.ini 内容格式
     */
    private static final String BANGUMI_INI_SEASON = """
            [Bangumi]
            id={}
            offset={}
            """;

    /**
     * 顶层 bangumi.ini 内容格式
     */
    private static final String BANGUMI_INI_ROOT = """
            [Bangumi]
            id={}
            """;

    /**
     * 保存 bangumi.ini
     * 顶层保存整剧条目 id, 季目录保存条目 id 与剧集偏移
     * 每部番剧只生成一次, 生成过就不再重新生成 (包括强制刮削与目录被移动后重建)
     * 标记文件位于配置目录 bangumi_ini/ 下 以条目id命名 删除标记与ini后可重新生成
     *
     * @param ani   订阅
     * @param force 兼容上游签名 无实际作用 (有标记文件就不会重复生成)
     */
    public void saveBangumiIni(Ani ani, Boolean force) {
        if (!Boolean.TRUE.equals(CONFIG.getBangumiIniEnabled())) {
            // 未开启 bangumi.ini
            return;
        }

        String subjectId = BgmUtil.getSubjectId(ani);
        if (StrUtil.isBlank(subjectId)) {
            return;
        }

        // 生成标记 每部番剧只生成一次
        File markerFile = new File(ConfigUtil.getConfigDir(), "bangumi_ini/" + subjectId);
        if (markerFile.exists()) {
            return;
        }

        String downloadPath = downloadService.getDownloadPath(ani);

        File downloadPathFile = new File(downloadPath);
        if (!FileUtil.exist(downloadPathFile)) {
            // 下载位置还不存在 等下载器创建目录后再生成
            return;
        }

        // 季目录 条目id 与剧集偏移
        File seasonFile = new File(downloadPathFile, "bangumi.ini");
        if (!seasonFile.exists()) {
            String seasonIni = StrFormatter.format(BANGUMI_INI_SEASON, subjectId,
                    Optional.ofNullable(ani.getOffset()).orElse(0));
            FileUtil.writeUtf8String(seasonIni, seasonFile);
            log.info("已保存 {}", seasonFile);
        }

        if (Boolean.TRUE.equals(ani.getOva())) {
            // 剧场版的下载位置就是影片目录 不再往上一层写
            FileUtil.touch(markerFile);
            return;
        }

        // 顶层 整剧条目id
        File parentFile = downloadPathFile.getParentFile();
        if (parentFile == null) {
            return;
        }
        File rootFile = new File(parentFile, "bangumi.ini");
        if (!rootFile.exists()) {
            FileUtil.writeUtf8String(StrFormatter.format(BANGUMI_INI_ROOT, subjectId), rootFile);
            log.info("已保存 {}", rootFile);
        }

        FileUtil.touch(markerFile);
    }

}
