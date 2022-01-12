package com.intellij.remoterobot.launcher

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.Locators
import com.intellij.remoterobot.utils.hasSingleComponent
import com.intellij.remoterobot.utils.waitFor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import javax.swing.Box
import javax.swing.JDialog

@ExtendWith(CommandLineProjectTest.IdeTestWatcher::class)
class CommandLineProjectTest {
    companion object {
        private var ideaProcess: Process? = null
        private var tmpDir: Path = Files.createTempDirectory("launcher")
        private lateinit var remoteRobot: RemoteRobot

        @BeforeAll
        @JvmStatic
        fun init() {
            StepWorker.registerProcessor(StepLogger())
        }

        @BeforeAll
        @JvmStatic
        fun startIdea() {
            val client = OkHttpClient()
            remoteRobot = RemoteRobot("http://localhost:8082", client)
            val ideDownloader = IdeDownloader(client)
            step("Start ide") {
                ideaProcess = IdeLauncher.launchIde(
                    ideDownloader.downloadAndExtractLatestEap(Ide.IDEA_COMMUNITY, tmpDir),
                    mapOf("robot-server.port" to 8082),
                    emptyList(),
                    listOf(ideDownloader.downloadRobotPlugin(tmpDir)),
                    tmpDir
                )
            }
            step("Wait for ide started") {
                waitFor(Duration.ofSeconds(90), Duration.ofSeconds(5)) {
                    remoteRobot.isAvailable()
                }
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanUp() {
//            ideaProcess?.destroy(30, TimeUnit.SECONDS)
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun commandLineProjectTest() {
        step("Click on 'Create New Project'") {
            remoteRobot.find<CommonContainerFixture>(
                Locators.byProperties(Locators.XpathProperty.SIMPLE_CLASS_NAME to "FlatWelcomeFrame"),
                Duration.ofSeconds(20)
            )
                .button(byXpath("""//div[contains(@defaulticon, 'createNewProject') or (@accessiblename='New Project' and @class='JBOptionButton')]"""))
                .click()
        }
        step("Setup project") {
            remoteRobot.find<CommonContainerFixture>(
                Locators.byTypeAndProperties(
                    JDialog::class.java,
                    Locators.XpathProperty.ACCESSIBLE_NAME to "New Project"
                ), Duration.ofSeconds(10)
            ).run {
                jList().clickItem("Java")
                button("Next").click()
                checkBox("Create project from template").select()
                jList().clickItem("Command Line App")
                button("Next").click()
                button("Finish").click()
            }
        }
        remoteRobot.find<CommonContainerFixture>(Locators.byProperties(Locators.XpathProperty.SIMPLE_CLASS_NAME to "IdeFrameImpl"), Duration.ofSeconds(20)).run {
            step("Close Tip of the Day") {
                find<CommonContainerFixture>(
                    Locators.byProperties(Locators.XpathProperty.ACCESSIBLE_NAME to "Tip of the Day"),
                    Duration.ofSeconds(20)
                )
                    .button("Close").click()
            }
            runCatching { button("Got It").click() }
            step("Run 'Main'") {
                find<CommonContainerFixture>(Locators.byType(Box::class.java))
                    .find<ComponentFixture>(Locators.byProperties(Locators.XpathProperty.TOOLTIP to "Run 'Main'"))
                    .click()
            }
            step("Check output") {
                waitFor(Duration.ofSeconds(30)) {
                    hasSingleComponent(Locators.byPropertiesContains(Locators.XpathProperty.TEXT to "Process finished with exit code 0"))
                }
            }
        }
    }

    class IdeTestWatcher: TestWatcher {
        override fun testFailed(context: ExtensionContext, cause: Throwable?) {
            if (!ImageIO.write(remoteRobot.getScreenshot(), "png", File("build/reports", "${context.displayName}.png"))) {
                println("failed to save screenshot")
            }
        }
    }
}