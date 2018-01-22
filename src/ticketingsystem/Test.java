package ticketingsystem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Random;

class TestResult {
    public double avgSingleBuyTicketTime;
    public double avgSingleRefundTime;
    public double avgSingleInquiryTime;
    public double throughput;

    public TestResult() {

    }

    /**
     * @param avgSingleBuyTicketTime 测试中每个线程执行购票操作耗时
     * @param avgRefundTime 测试中每个线程执行退票操作耗时
     * @param avgInquiryTime 测试中每个线程执行查询操作耗时
     * @param throughput 系统总吞吐量
     */
    public TestResult(double avgSingleBuyTicketTime, double avgRefundTime, double avgInquiryTime, double throughput) {
        this.avgSingleBuyTicketTime = avgSingleBuyTicketTime;
        this.avgSingleRefundTime = avgRefundTime;
        this.avgSingleInquiryTime = avgInquiryTime;
        this.throughput = throughput;
    }

    @Override
    public String toString() {
        BigDecimal b1 = new BigDecimal(avgSingleBuyTicketTime);
        avgSingleBuyTicketTime = b1.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
        BigDecimal b2 = new BigDecimal(avgSingleInquiryTime);
        avgSingleInquiryTime = b2.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();
        BigDecimal b3 = new BigDecimal(avgSingleRefundTime);
        avgSingleRefundTime = b3.setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue();

        return "TestResult{" +
                "avgSingleBuyTicketTime=" + avgSingleBuyTicketTime + "ms" +
                ", avgSingleRefundTime=" + avgSingleRefundTime + "ms" +
                ", avgSingleInquiryTime=" + avgSingleInquiryTime + "ms" +
                ", throughput=" + String.format("%.2f", throughput) + "times/s" +
                '}';
    }
}

public class Test {
    final static int inqpc = 100;
    final static int retpc = 10;
    final static int buypc = 40;


    public String passengerName(int testnum) {
        Random rand = new Random();
        long uid = rand.nextInt(testnum);
        return "passenger" + uid;
    }

    public TestResult test(final int threadnum, final int testnum, final int routenum, final int coachnum, final int seatnum, final int stationnum) throws Exception {
        Thread[] threads = new Thread[threadnum];

        // 用于计算单线程方法平均耗时
        final long[] functionStartTime = new long[threadnum];

        final long[][] functionCostTimeSum = new long[threadnum][3];

        final int[][] executeCount = new int[threadnum][3];

        long startTime = System.currentTimeMillis();
        final TicketingDS tds = new TicketingDS(routenum, coachnum, seatnum, stationnum, threadnum);

        for (int i = 0; i < threadnum; i++) {
            final int finalI = i;
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    Random rand = new Random();
                    Ticket ticket;
                    ArrayList<Ticket> soldTicket = new ArrayList<>();

                    for (int j = 0; j < testnum; j++) {
                        int sel = rand.nextInt(inqpc);
                        functionStartTime[finalI] = System.currentTimeMillis();
                        if (0 <= sel && sel < retpc && soldTicket.size() > 0) { //  refund ticket
                            int select = rand.nextInt(soldTicket.size());
                            if ((ticket = soldTicket.remove(select)) != null) {
                                if (tds.refundTicket(ticket)) {
                                } else {
                                    // nothing
                                }
                            } else {
                                //nothing
                            }
                            executeCount[finalI][0]++;
                            functionCostTimeSum[finalI][0] += System.currentTimeMillis() - functionStartTime[finalI];
                        } else if (retpc <= sel && sel < buypc) { // buy ticket
                            String passenger = passengerName(testnum);
                            int route = rand.nextInt(routenum) + 1;
                            int departure = rand.nextInt(stationnum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                            if ((ticket = tds.buyTicket(passenger, route, departure, arrival)) != null) {
                                soldTicket.add(ticket);
                            } else {
                            }
                            executeCount[finalI][1]++;
                            functionCostTimeSum[finalI][1] += System.currentTimeMillis() - functionStartTime[finalI];
                        } else if (buypc <= sel && sel < inqpc) { // inquiry ticket
                            int route = rand.nextInt(routenum) + 1;
                            int departure = rand.nextInt(stationnum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationnum - departure) + 1; // arrival is always greater than departure
                            int leftTicket = tds.inquiry(route, departure, arrival);
                            executeCount[finalI][2]++;
                            functionCostTimeSum[finalI][2] += System.currentTimeMillis() - functionStartTime[finalI];
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < threadnum; i++) {
            threads[i].join();
        }
        long totalTime = System.currentTimeMillis() - startTime; //获取总时间

        double avgBuy = 0, avgRefund = 0, avgInqui = 0, totalCount = 0;
        for (int i = 0; i < threadnum; i++) {
//            totalTime += functionCostTimeSum[i][0] +  functionCostTimeSum[i][0] + functionCostTimeSum[i][2];
            totalCount += executeCount[i][0] + executeCount[i][1] + executeCount[i][2];
            avgRefund += functionCostTimeSum[i][0] * 1.0 / executeCount[i][0];
            avgBuy += functionCostTimeSum[i][1] * 1.0 / executeCount[i][1];
            avgInqui += functionCostTimeSum[i][2] * 1.0 / executeCount[i][2];
        }
        avgBuy /= threadnum;
        avgRefund /= threadnum;
        avgInqui /= threadnum;

        TestResult tr = new TestResult(avgBuy, avgRefund, avgInqui, 1.0 * totalCount * 1000 / totalTime);

        return tr;

    }

    public TestResult test(int threadnum) throws Exception {
        return test(threadnum, 10000, 5, 8, 100, 10);
    }

    public TestResult test() throws Exception {
        return test(16);
    }

    public static void main(String[] args) throws Exception {
        int[] thread_nums = {4, 8, 16, 32, 64};
        int each = 5;

        Test test = new Test();

        double[] result = new double[thread_nums.length];
        TestResult[] testResult = new TestResult[thread_nums.length];
        for (int i = 0; i < thread_nums.length; i++) {
            testResult[i] = new TestResult();
            for (int j = 0; j < each; j++) {
                TestResult tmp = test.test(thread_nums[i]);
                testResult[i].avgSingleBuyTicketTime += tmp.avgSingleBuyTicketTime;
                testResult[i].avgSingleInquiryTime += tmp.avgSingleInquiryTime;
                testResult[i].avgSingleRefundTime += tmp.avgSingleRefundTime;
                testResult[i].throughput += tmp.throughput;
            }
            testResult[i].avgSingleBuyTicketTime /= each;
            testResult[i].avgSingleRefundTime /= each;
            testResult[i].avgSingleInquiryTime /= each;
            testResult[i].throughput /= each;
            System.out.println("Thread: " + thread_nums[i] + "\n" + testResult[i]);
        }
        // TODO 验证多线程的正确性
    }
}
