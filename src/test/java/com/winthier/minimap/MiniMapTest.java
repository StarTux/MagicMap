package com.winthier.minimap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.Assert;
import org.junit.Test;

public final class MiniMapTest {
    @Test
    public void main() throws Exception {
        BufferedImage image = ImageIO.read(new File("src/main/resources/Font4x4.png"));
        Font4x4 font4x4 = new Font4x4(image);
    }
}
