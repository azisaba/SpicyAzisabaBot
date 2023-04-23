package net.azisaba.spicyazisababot.util

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageUtil {
    fun resizeImage(image: Image, width: Int, height: Int): BufferedImage {
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = bufferedImage.createGraphics()
        graphics.drawImage(image, 0, 0, width, height, null)
        graphics.dispose()
        return bufferedImage
    }

    fun imageToBytes(image: Image): ByteArray {
        val resizedImage = resizeImage(image, 100, 100)
        val byteArrayOutputStream = ByteArrayOutputStream()
        ImageIO.write(resizedImage, "png", byteArrayOutputStream)
        return byteArrayOutputStream.toByteArray()
    }
}
