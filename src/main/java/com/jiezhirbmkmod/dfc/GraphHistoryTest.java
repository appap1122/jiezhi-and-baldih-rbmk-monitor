package com.jiezhirbmkmod.dfc;

public final class GraphHistoryTest {

    private static int passed = 0;
    private static int failed = 0;

    private GraphHistoryTest() {
    }

    public static void main(String[] args) {
        GraphHistoryTest.testRingBuffer();
        GraphHistoryTest.testGaps();
        GraphHistoryTest.testNiceCeil();
        GraphHistoryTest.testAxisMax();
        GraphHistoryTest.testGridValues();
        System.out.println();
        System.out.println("PASS " + passed + " / FAIL " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRingBuffer() {
        GraphHistory h = new GraphHistory();
        GraphHistoryTest.assertTrue(h.size() == 0, "空历史 size=0");
        GraphHistoryTest.assertTrue(!h.hasData(), "空历史 hasData=false");
        for (int i = 0; i < GraphHistory.CAPACITY + 20; i++) {
            h.push(i, i, i, i, 0.5, 0.1);
        }
        GraphHistoryTest.assertTrue(h.size() == GraphHistory.CAPACITY,
                "写满后 size 封顶 CAPACITY（实际 " + h.size() + "）");
        GraphHistoryTest.assertTrue(Math.abs(h.valueAt(0, 0) - 20.0) < 1e-9,
                "最旧点被覆盖（期望 20，实际 " + h.valueAt(0, 0) + "）");
        GraphHistoryTest.assertTrue(Math.abs(h.valueAt(0, h.size() - 1) - (GraphHistory.CAPACITY + 19)) < 1e-9,
                "最新点在队尾");
        h.push(-5.0, -1.0, -2.0, -3.0, -0.1, -0.2);
        GraphHistoryTest.assertTrue(h.valueAt(0, h.size() - 1) == 0.0, "负数钳到 0");
        h.push(1, 1, 1, 1, 2.5, 0.5);
        GraphHistoryTest.assertTrue(h.valueAt(4, h.size() - 1) == 1.0, "富集度钳到 1");
    }

    private static void testGaps() {
        GraphHistory h = new GraphHistory();
        h.push(100, 10, 1000, 500, 0.5, 0.1);
        h.pushGap();
        h.push(200, 20, 2000, 600, 0.5, 0.1);
        GraphHistoryTest.assertTrue(!h.gapAt(0), "第 0 点不是断点");
        GraphHistoryTest.assertTrue(h.gapAt(1), "第 1 点是断点");
        GraphHistoryTest.assertTrue(!h.gapAt(2), "第 2 点不是断点");
        double v = h.valueAt(0, 1);
        GraphHistoryTest.assertTrue(Double.isNaN(v), "断点读数为 NaN 而不是 0");
        GraphHistory h2 = new GraphHistory();
        h2.pushGap();
        h2.pushGap();
        GraphHistoryTest.assertTrue(!h2.hasData(), "全断点 hasData=false");
        GraphHistory h3 = new GraphHistory();
        h3.push(Double.NaN, 1, 1, 1, 1, 1);
        GraphHistoryTest.assertTrue(h3.gapAt(0), "NaN 输入记为断点");
    }

    private static void testNiceCeil() {
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(100.0) == 100.0, "100 -> 100");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(101.0) == 150.0, "101 -> 150");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(250.0) == 250.0, "250 -> 250");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(812.5) == 1000.0, "812.5 -> 1000");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(1240.0) == 1500.0, "1240 -> 1500");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(3587.0) == 4000.0, "3587 -> 4000");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(0.0) == 1.0, "0 -> 1（非法输入兜底）");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(-1.0) == 1.0, "负数 -> 1（兜底）");
        GraphHistoryTest.assertTrue(GraphHistory.niceCeil(100.0) == 100.0, "容差：100 不被抬高");
        double[] probes = {1.5, 7.3, 99.9, 100.1, 999.99, 1000.0, 12345.6, 999999.9};
        for (double p : probes) {
            GraphHistoryTest.assertTrue(GraphHistory.niceCeil(p) >= p, "niceCeil(" + p + ") >= 入参");
        }
    }

    private static void testAxisMax() {
        GraphHistory h = new GraphHistory();
        GraphHistoryTest.assertTrue(h.axisMax(0) >= GraphHistory.HEAT_BASE_MAX, "柱温轴不小于底值");
        GraphHistoryTest.assertTrue(h.axisMax(1) >= GraphHistory.FLUX_BASE_MAX, "通量轴不小于底值");
        GraphHistoryTest.assertTrue(h.axisMax(2) >= GraphHistory.WATER_BASE_MAX, "水量轴不小于底值");
        for (int i = 0; i < 5; i++) {
            h.push(2500.0, 1, 1, 1, 1, 1);
        }
        GraphHistoryTest.assertTrue(h.axisMax(0) >= 2500.0, "柱温轴包住峰值 2500");
        GraphHistoryTest.assertTrue(h.axisMax(0) == 2500.0 || h.axisMax(0) == 3000.0,
                "柱温轴抬到好看刻度（实际 " + h.axisMax(0) + "）");
    }

    private static void testGridValues() {
        GraphHistoryTest.assertTrue(GraphHistory.gridValue(200.0, 0) == 200.0, "grid k=0 = 上界");
        GraphHistoryTest.assertTrue(GraphHistory.gridValue(200.0, 1) == 150.0, "grid k=1 = 75%");
        GraphHistoryTest.assertTrue(GraphHistory.gridValue(200.0, 2) == 100.0, "grid k=2 = 50%");
        GraphHistoryTest.assertTrue(GraphHistory.gridValue(200.0, 4) == 0.0, "grid k=4 = 0");
    }

    private static void assertTrue(boolean cond, String msg) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + msg);
        }
    }
}
