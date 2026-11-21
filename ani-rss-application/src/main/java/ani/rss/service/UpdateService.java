package ani.rss.service;

import ani.rss.commons.ExceptionUtils;
import ani.rss.commons.MavenUtils;
import ani.rss.entity.About;
import ani.rss.entity.UpdateInfo;
import ani.rss.update.BaseUpdate;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.thread.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateService {

    private final GithubService githubService;

    /**
     * 关于
     *
     * @return 关于信息
     */
    public synchronized About about() {
        String version = MavenUtils.getVersion();

        About about = (About) new About()
                .setVersion(version)
                .setUpdate(false)
                .setAutoUpdate(false)
                .setLatest("")
                .setMarkdownBody("");
        try {
            MavenUtils.CurrentFile currentFile = MavenUtils.getCurrentFile();

            String filename = currentFile.isJar() ? "ani-rss.jar" : "ani-rss.exe";

            // 更新源改为本仓库Release 由CI自动构建打补丁版本 版本号与上游保持一致
            // 自动更新(定时任务)与设置-关于-更新(手动)都经由这里检测与下载
            UpdateInfo updateInfo = githubService.getUpdateInfo("chen8945", "ani-rss-patch", filename, version);

            BeanUtil.copyProperties(updateInfo, about, "version");
        } catch (Exception e) {
            String message = ExceptionUtils.getMessage(e);
            log.error("检测更新失败 {}", message);
            log.error(message, e);
        }
        return about;
    }

    /**
     * 更新程序
     *
     * @param about 关于信息
     */
    public synchronized void update(About about) {
        Boolean update = about.getUpdate();
        if (!update) {
            return;
        }

        // 自构建版本 更新源指向本仓库Release 拉取的也是打过补丁的jar 补丁不会被覆盖
        MavenUtils.CurrentFile currentFile = MavenUtils.getCurrentFile();

        Assert.isTrue(currentFile.isFile(), "不支持更新");

        BaseUpdate baseUpdate = BaseUpdate.getInstance();

        File updateFile = baseUpdate.downloadUpdateFile(about);

        ThreadUtil.execute(() -> {
            try {
                baseUpdate.update(updateFile);
            } catch (Exception e) {
                log.error("更新时遇到错误: {}", e.getMessage(), e);
            }
        });
    }
}
