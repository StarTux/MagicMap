package com.cavetale.magicmap;

import com.cavetale.core.font.Unicode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MagicMapScale {
    SCALE_0(0.5, 1, 64, "2"),
    SCALE_1(1.0, 1, 128, "1"),
    SCALE_2(1.5, 2, 192, over(2, 3)),
    SCALE_3(2.0, 2, 256, over(1, 2)),
    SCALE_4(3.0, 3, 384, over(1, 3)),
    SCALE_5(4.0, 4, 512, over(1, 4)),
    SCALE_6(5.0, 5, 640, over(1, 5)),
    SCALE_7(6.0, 6, 768, over(1, 6)),
    SCALE_8(8.0, 8, 1024, over(1, 8));

    public static final MagicMapScale DEFAULT = SCALE_1;

    private static String over(int a, int b) {
        return Unicode.superscript(a) + "/" + Unicode.subscript(b);
    }

    public final double scale;
    public final int minWalkingDistance;
    public final int size;
    public final String zoomFormat;
}
