package com.nerve.android.screenshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDetectorTest {
    @Test fun `relativePath 含 Screenshots 判为截图`() {
        assertTrue(isScreenshot(relativePath = "Pictures/Screenshots/", bucketName = null))
        assertTrue(isScreenshot(relativePath = "DCIM/Screenshots/", bucketName = null))
    }

    @Test fun `bucketName 为 Screenshots 判为截图`() {
        assertTrue(isScreenshot(relativePath = null, bucketName = "Screenshots"))
    }

    @Test fun `普通照片不是截图`() {
        assertFalse(isScreenshot(relativePath = "DCIM/Camera/", bucketName = "Camera"))
        assertFalse(isScreenshot(relativePath = null, bucketName = null))
    }

    @Test fun `大小写不敏感`() {
        assertTrue(isScreenshot(relativePath = "Pictures/screenshots/", bucketName = null))
    }

    @Test fun `dedup —— 同一个 uri 只处理一次`() {
        val seen = SeenUris()
        assertTrue(seen.firstTimeFor("content://media/external/images/media/1"))
        assertFalse(seen.firstTimeFor("content://media/external/images/media/1"))
        assertTrue(seen.firstTimeFor("content://media/external/images/media/2"))
    }

    // ---- isCameraPhoto tests (TDD: written before implementation) ----

    @Test fun `DCIM Camera 路径判为相机照片`() {
        assertTrue(isCameraPhoto(relativePath = "DCIM/Camera/", bucketName = null))
    }

    @Test fun `bucketName 为 Camera 判为相机照片`() {
        assertTrue(isCameraPhoto(relativePath = null, bucketName = "Camera"))
    }

    @Test fun `isCameraPhoto 大小写不敏感`() {
        assertTrue(isCameraPhoto(relativePath = "dcim/camera/", bucketName = null))
        assertTrue(isCameraPhoto(relativePath = null, bucketName = "camera"))
    }

    @Test fun `截图路径不是相机照片`() {
        assertFalse(isCameraPhoto(relativePath = "Pictures/Screenshots/", bucketName = null))
    }

    @Test fun `null null 不是相机照片`() {
        assertFalse(isCameraPhoto(relativePath = null, bucketName = null))
    }
}
