package ticketingsystem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TicketingDS implements TicketingSystem {
    private final int ROUTE_NUM;
    private final int TICKETS_PER_STATION;
    private final int COACH_NUM;
    private final int SEAT_NUM;
    private final int STATION_NUM;

    private int ranges;
    private SegmentTree[] routes_tickets_num;
    private SeatBitMap[] routes_seats;

    class SeatBitMap {
        // 车厢数
        private int coach_num;
        // 每节车厢座位数
        private int seat_num;
        // 总座位数
        private int total_seat_num;

        private AtomicLong[] seat_bit_map;

        // 记录当前车票的上一次购票index，针对探测器构造历史
        private ConcurrentHashMap<Long, AtomicInteger> history;

        public SeatBitMap(int coach_num, int seat_num) {
            this.coach_num = coach_num;
            this.seat_num = seat_num;
            this.total_seat_num = coach_num * seat_num;

            seat_bit_map = new AtomicLong[total_seat_num];
            for (int i = 0; i < total_seat_num; i++) {
                seat_bit_map[i] = new AtomicLong();
            }

            history = new ConcurrentHashMap<Long, AtomicInteger>();
        }


        /**
         * 使用bit来表示某对应区段的座位是否被占用, 1表示占用
         *
         * @param departure 始发站
         * @param arrival   终点站
         * @return {车厢号， 座位号}
         * @throws Exception
         */
        public int[] allocateSeat(int departure, int arrival) {
            int[] result = {0, 0};

            //TODO 构造探测器
            long detector = getDetector(departure, arrival);

            //TODO 如果不存在，构造新的记录
            if (!history.containsKey(detector)) {
                history.put(detector, new AtomicInteger());
            }

            long new_seat_flag_bit, seat_flag_bit;
            AtomicInteger curr_history = history.get(detector);
            int curr_index = curr_history.get();
            for (int i = curr_index; i < total_seat_num; i++) {
                //TODO find seat
                AtomicLong curr_flag_bit = seat_bit_map[i];
                seat_flag_bit = curr_flag_bit.get();
                // 找到探测器范围内bit值都为0的座位
                while ((seat_flag_bit & detector) == 0) {
                    // 将对应区段都改为1标志
                    new_seat_flag_bit = seat_flag_bit | detector;

                    if (curr_flag_bit.compareAndSet(seat_flag_bit, new_seat_flag_bit)) {
                        result = new int[]{i / SEAT_NUM + 1, i % SEAT_NUM + 1};
                        // 更新最近购票位
                        curr_history.compareAndSet(curr_index, i + 1);
                        return result;
                    }
                    seat_flag_bit = curr_flag_bit.get();
                }
            }
            return result;
        }

        /**
         * 回收退票对应的座位
         *
         * @param departure 始发站
         * @param arrival   终点站
         * @param coach     车厢号
         * @param seat      座位号
         * @return
         * @throws Exception
         */
        public boolean recycleSeat(int departure, int arrival, int coach, int seat) throws Exception {
            int index = (coach - 1) * SEAT_NUM + seat - 1;
            AtomicLong curr_flag_bit = seat_bit_map[index];
            long seat_flag_bit = curr_flag_bit.get();
            long new_seat_flag_bit;

            // 获取余票检测器
            long detector = getDetector(departure, arrival);

            // 在探测器范围内的bit都应该是1，否则出错
            AtomicInteger curr_history = history.get(detector);
            while ((seat_flag_bit & detector) == detector) {
                new_seat_flag_bit = (~detector) & seat_flag_bit;
                if (curr_flag_bit.compareAndSet(seat_flag_bit, new_seat_flag_bit)) {
                    // TODO 如何保证小于历史的index一定都被记录到？
                    int expect = curr_history.get();
                    while (index < expect) {
                        curr_history.compareAndSet(expect, index);
                        expect = curr_history.get();
                    }
                    return true;
                }
                seat_flag_bit = curr_flag_bit.get();
            }
            System.out.println("Can't recycle seat, something wrong!");
            System.out.flush();
            return false;
        }

        /**
         * 返回剩余的车票数
         *
         * @param departure 始发站
         * @param arrival   终点站
         * @return 余票
         */
        public int query(int departure, int arrival) {
            int count = 0;
            long detector = getDetector(departure, arrival);

            int curr_index = 0;
            if (history.containsKey(detector)) {
                AtomicInteger curr_history = history.get(detector);
                curr_index = curr_history.get();
            }
            for (int i = curr_index; i < total_seat_num; i++) {
                AtomicLong curr_seat_bit = seat_bit_map[i];
                if ((curr_seat_bit.get() & detector) == 0)
                    count++;
            }
            return count;
        }

        /**
         * 返回对应区间的余票检测器
         *
         * @param departure
         * @param arrival
         * @return
         */
        private long getDetector(int departure, int arrival) {
            long detector = 0;
            for (int j = departure - 1; j < arrival - 1; j++) {
                detector += 1 << j;
            }
            return detector;
        }

    }

    public TicketingDS(int routenum, int coachnum, int seatnum, int stationnum, int threadnum) throws InterruptedException{
        // 验证数据是否合法
        if (stationnum > 64)
            throw new InterruptedException("不支持64个以上的站点");

        ROUTE_NUM = routenum;
        COACH_NUM = coachnum;
        SEAT_NUM = seatnum;
        STATION_NUM = stationnum;

        // 每车次每段区间的初始余票
        TICKETS_PER_STATION = COACH_NUM * SEAT_NUM;

        ranges = STATION_NUM - 1;
        // 所有车次当前余座
        routes_seats = new SeatBitMap[routenum];
        for (int i = 0; i < routenum; i++) {
            // 初始化座位
            routes_seats[i] = new SeatBitMap(COACH_NUM, SEAT_NUM);
        }
    }

    public void printState() {
        System.out.println("Routenum: " + routes_tickets_num.length);
        System.out.println("Stations: " + STATION_NUM);
        System.out.println("Tickets per station: " + TICKETS_PER_STATION);
    }

    @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival) {

        int[] seat = routes_seats[route - 1].allocateSeat(departure, arrival);
        if (seat[0] != 0) {
            return TicketFactory.ConstructTicket(passenger, route, departure, arrival,
                    seat[0], seat[1]);
        } else
            return null;
    }

    @Override
    public int inquiry(int route, int departure, int arrival) {
        int result = routes_seats[route - 1].query(departure, arrival);
        return result;
    }

    @Override
    public boolean refundTicket(Ticket ticket) {
        try {
            if(routes_seats[ticket.route - 1].recycleSeat(ticket.departure, ticket.arrival,
                    ticket.coach, ticket.seat))
                return true;
            else
                return false;
        } catch (IndexOutOfBoundsException e) {
            System.err.println(e);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /*
    we don't need it
     */
    @Override
    public boolean buyTicketReplay(Ticket ticket) {
        return false;
    }

    /*
    we don't need it
     */
    @Override
    public boolean refundTicketReplay(Ticket ticket) {
        return false;
    }
}
