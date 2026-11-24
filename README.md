# anatawa12's ForgeGradle 1.2 fork for Gradle 4.4.1+ - example project

This is an example mod using the [fork of ForgeGradle-1.2 made by anatawa12](https://github.com/anatawa12/ForgeGradle-1.2).
This fork supports Gradle 4.4.1 and later. This example project uses Gradle 5.6.4.

## How to use this example project

You can download this example project from [here](https://github.com/anatawa12/ForgeGradle-example/archive/master.zip), or use it as a template on Github.
This project can be used as a replacement for Forge's 1.7.10 MDK.

## How to replace ForgeGradle 1.2. with anatawa12's fork
Although this example project has some differences to Forge's 1.7.10 MDK, anatawa12's fork of ForgeGradle 1.2 can be used by most projects with only minimal changes to their Gradle build script.

Here is a list of changes to Forge's 1.7.10 MDK Gradle build script, to replace the official ForgeGradle 1.2 plugin with the fork. These changes are likely to work with most projects based on Forge's 1.7.10 MDK.

In the repositories block of the buildscript section, add jcenter, and switch the Forge maven to use HTTPS instead of HTTP:
```diff
     repositories {
         mavenCentral()
         maven {
             name = "forge"
-            url = "http://files.minecraftforge.net/maven"
+            url = "https://maven.minecraftforge.net/"
         }
```

Also in the dependencies block of the buildscript section, change the dependency on Forge's official ForgeGradle 1.2 to the fork:
```diff
     dependencies {
-        classpath 'net.minecraftforge.gradle:ForgeGradle:1.2-SNAPSHOT'
+        classpath ('com.anatawa12.forge:ForgeGradle:1.2-1.1.+') {
+            changing = true
+        }
     }
```

The Gradle wrapper should also be changed to use Gradle 4.4.1 or higher. <!--Currently, the plugin [does not support Gradle 6.x](https://github.com/anatawa12/ForgeGradle-1.2/issues/9), although this may change in the future. As such, the latest version of Gradle this plugin supports is Gradle 5.6.4.-->

## 多媒体依赖安装说明（RBMK-Monitor 15.2-beta）

4x3 多媒体屏幕的 MP4（H.264）/ MP3（AAC）解码依赖是可选的 JavaCV / FFmpeg 运行库，不装也能正常进游戏，只是屏幕会显示 `[UNSUPPORTED CODEC]`。

> **平台限制**：目前仅提供 Windows 64 位（x86_64）的依赖包。Mac/Linux 玩家暂不支持多媒体播放，但中控、红石、黑客等其他功能不受影响。

**1. 依赖包放置路径**

下载 `RBMK-Media-Deps-Windows.zip`，解压后把里面的**全部 `.jar` 文件**放到：

```
.minecraft/mods/
```

（和 `RBMK-Monitor-15.2-beta.jar` 放在同一个文件夹，重启游戏即可生效。）

如果服务端/整合包不允许往 `mods/` 里放非模组 jar，可改用回退目录：

```
.minecraft/rbmk_media/libs/
```

模组会先尝试常规 classpath，找不到时再从这个目录加载。

**2. 媒体文件放置路径**

MP4 / MP3 文件统一放在：

```
.minecraft/rbmk_media/
```

例如 `.minecraft/rbmk_media/demo.mp4`。

**3. 终端指令**

打开终端屏幕后输入：

```
media play demo.mp4
media pause
media resume
media stop
media volume 80
media install
```

`media install` 可随时重新调出多媒体依赖安装引导界面。

