package app.awavenue.c001apk.ui.feed.detail

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OssUploadTest {

    @Test
    fun livePhotoEmitsImageLiveFieldsAndCompanionVideoEntry() {
        val liveImage = SelectedImage(
            path = "/tmp/still.jpg",
            model = OssUploadModel("still.jpg", "1080x1440", "img-md5", livePhotoVideo = "vid.mp4"),
            liveVideo = LivePhotoVideo("/tmp/vid.mp4", "vid.mp4", "vid-md5"),
            livePhotoEnabled = true,
        )

        val list = OssUpload.buildUploadFileList(listOf(liveImage), enableLivePhoto = true)

        assertEquals(2, list.size)
        val still = list[0]
        assertEquals("still.jpg", still["name"]?.jsonPrimitive?.content)
        assertEquals("1080x1440", still["resolution"]?.jsonPrimitive?.content)
        assertEquals("img-md5", still["md5"]?.jsonPrimitive?.content)
        assertEquals(0, still["hdr"]?.jsonPrimitive?.int)
        assertEquals("vid.mp4", still["livePhotoVideo"]?.jsonPrimitive?.content)
        assertEquals(1, still["livePhoto"]?.jsonPrimitive?.int)

        val companion = list[1]
        assertEquals("vid.mp4", companion["name"]?.jsonPrimitive?.content)
        assertEquals("vid-md5", companion["md5"]?.jsonPrimitive?.content)
        assertNull(companion["resolution"])
    }

    @Test
    fun toggledOffOrMessageUploadStripsLivePhotoFieldsAndCompanionVideo() {
        val toggledOff = SelectedImage(
            path = "/tmp/still.jpg",
            model = OssUploadModel("still.jpg", "1080x1440", "img-md5", livePhotoVideo = "vid.mp4"),
            liveVideo = LivePhotoVideo("/tmp/vid.mp4", "vid.mp4", "vid-md5"),
            livePhotoEnabled = false,
        )
        val liveEnabled = toggledOff.copy(livePhotoEnabled = true)

        for (list in listOf(
            OssUpload.buildUploadFileList(listOf(toggledOff), enableLivePhoto = true),
            OssUpload.buildUploadFileList(listOf(liveEnabled), enableLivePhoto = false),
        )) {
            assertEquals(1, list.size)
            val still = list[0]
            assertEquals("still.jpg", still["name"]?.jsonPrimitive?.content)
            assertFalse("livePhoto" in still)
            assertFalse("livePhotoVideo" in still)
        }
    }

    @Test
    fun resolveFileInfoMatchesByNameRegardlessOfOrder() {
        val array = JsonArray(
            listOf(
                JsonObject(mapOf("name" to JsonPrimitive("vid.mp4"), "uploadFileName" to JsonPrimitive("feed/v.mp4"))),
                JsonObject(mapOf("name" to JsonPrimitive("still.jpg"), "uploadFileName" to JsonPrimitive("feed/s-livepic.jpg"))),
            ),
        )

        val map = OssUpload.resolveFileInfoByName(array)

        assertEquals("feed/s-livepic.jpg", map["still.jpg"]?.get("uploadFileName")?.jsonPrimitive?.content)
        assertEquals("feed/v.mp4", map["vid.mp4"]?.get("uploadFileName")?.jsonPrimitive?.content)
    }
}
