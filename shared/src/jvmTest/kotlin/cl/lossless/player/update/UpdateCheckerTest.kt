package cl.lossless.player.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val releases = """
        [
          {"tag_name": "exp-1.2.60", "draft": false, "body": "Mini reproductor",
           "assets": [
             {"name": "ResuenApp-1.2.60-Setup.exe", "browser_download_url": "https://x/setup60.exe", "size": 10},
             {"name": "ResuenApp-exp-1.2.60.apk", "browser_download_url": "https://x/60.apk", "size": 20}
           ]},
          {"tag_name": "exp-1.2.61", "draft": true, "body": "",
           "assets": [{"name": "ResuenApp-exp-1.2.61.apk", "browser_download_url": "https://x/61.apk", "size": 20}]},
          {"tag_name": "exp-1.2.59", "draft": false, "body": "",
           "assets": [{"name": "ResuenApp-exp-1.2.59.apk", "browser_download_url": "https://x/59.apk", "size": 20}]},
          {"tag_name": "v1.3.0", "draft": false, "body": "",
           "assets": [{"name": "ResuenApp-1.3.0.apk", "browser_download_url": "https://x/s.apk", "size": 20}]},
          {"tag_name": "exp-1.2.100", "draft": false, "body": "",
           "assets": [{"name": "ResuenApp-1.2.100-macOS-intel.dmg", "browser_download_url": "https://x/i.dmg", "size": 30}]}
        ]
    """.trimIndent()

    private val checker = UpdateChecker("test", fetch = { releases })

    @Test
    fun `elige la versión más nueva del canal que trae el archivo pedido`() {
        val update = checker.check("1.2.55-exp", "exp") { it.endsWith(".apk") }
        assertEquals("1.2.60", update?.version)
        assertEquals("https://x/60.apk", update?.assetUrl)
        assertEquals("Mini reproductor", update?.notes)
    }

    @Test
    fun `ignora borradores, otros canales y versiones sin archivo para este equipo`() {
        val update = checker.check("1.2.55", "exp") { it.endsWith("-Setup.exe") }
        assertEquals("1.2.60", update?.version)
        val mac = checker.check("1.2.55", "exp") { it.endsWith("macOS-intel.dmg") }
        assertEquals("1.2.100", mac?.version)
    }

    @Test
    fun `al día no devuelve nada`() {
        assertNull(checker.check("1.2.60", "exp") { it.endsWith(".apk") })
    }

    @Test
    fun `compara número por número`() {
        assertTrue(UpdateChecker.compareVersions("1.2.100", "1.2.99") > 0)
        assertTrue(UpdateChecker.compareVersions("1.2.9-exp", "1.2.10") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.2", "1.2.0"))
    }
}
