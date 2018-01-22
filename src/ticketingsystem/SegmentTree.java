package ticketingsystem;

import java.util.Random;

public class SegmentTree {
    // 线段树数组
    private int[] tree_data;
    // 延迟增量累计，可以是负值
    private int[] addition_record;
    // 原始数据
    private int[] origin_data;

    private Object[] lock_targets;

    public SegmentTree(int[] data) {
        origin_data = data;
        tree_data = new int[data.length * 3];
        addition_record = new int[data.length * 3];

        lock_targets = new Object[origin_data.length * 3];
        for (int i = 0; i < origin_data.length * 3; i++)
            lock_targets[i] = new Object();
        buildTree(0, 0, data.length - 1);
    }

    /**
     * 构造线段树
     * @param root 当前子树的根节点
     * @param low  区间低端
     * @param high 区间高端
     */
    private void buildTree(int root, int low, int high) {
        addition_record[root] = 0;
        if (low == high) {
            tree_data[root] = origin_data[low];
        } else {
            int mid = (low + high) / 2;
            buildTree(root * 2 + 1, low, mid);
            buildTree(root * 2 + 2, mid + 1, high);
            tree_data[root] = Math.min(tree_data[root * 2 + 1], tree_data[root * 2 + 2]);
        }
    }

    /**
     * @param qu_low  查询区间低端
     * @param qu_high 查询区间高端
     * @return 该区间内最小值
     */
    public int query(int qu_low, int qu_high) {
        return query(0, 0, origin_data.length - 1, qu_low, qu_high);
    }

    private int query(int root, int curr_low, int curr_high, int qu_low, int qu_high) {
        // 查询区间和当前区间没有交集
        if (qu_low > curr_high || qu_high < curr_low)
            return Integer.MAX_VALUE;
        // 查询区包含当前区间
        if (qu_low <= curr_low && qu_high >= curr_high)
            return tree_data[root];
        pushDown(root);
        int mid = (curr_low + curr_high) / 2;
        return Math.min(query(root * 2 + 1, curr_low, mid, qu_low, qu_high),
                query(root * 2 + 2, mid + 1, curr_high, qu_low, qu_high));
    }

    /**
     * 将当前节点的延迟累计向孩子节点传递
     * 在查询的时候也可能存在更新操作
     *
     * @param root
     */
    private void pushDown(int root) {
        if (addition_record[root] != 0) {
            synchronized (this.lock_targets[root]) {
                addition_record[root * 2 + 1] += addition_record[root];
                addition_record[root * 2 + 2] += addition_record[root];

                // 区间内所有的值都加上延迟累计
                tree_data[root * 2 + 1] += addition_record[root];
                tree_data[root * 2 + 2] += addition_record[root];
                // 延迟累计已产生作用，故清零
                addition_record[root] = 0;
            }
        }
    }

    /**
     * 线段树区间更新操作
     * @param root 当前区间最小值的index
     * @param curr_low 当前区间低端
     * @param curr_high 当前区间高端
     * @param up_low 目标区间低端
     * @param up_high 目标区间高端
     * @param addition
     */
    private void update(int root, int curr_low, int curr_high, int up_low, int up_high, int addition) {
        // 当前区间和目标区间没有交集
        if (curr_high < up_low || curr_low > up_high)
            return;

        // 当前区间在目标区间内
        if (up_low <= curr_low && up_high >= curr_high) {
            synchronized (this.lock_targets[root]) {
                addition_record[root] += addition;
                tree_data[root] += addition;
            }
            return;
        }
        pushDown(root);
        int mid = (curr_high + curr_low) / 2;
        update(root * 2 + 1, curr_low, mid, up_low, up_high, addition);
        update(root * 2 + 2, mid + 1, curr_high, up_low, up_high, addition);
        tree_data[root] = Math.min(tree_data[root * 2 + 1], tree_data[root * 2 + 2]);
    }

    public void update(int up_low, int up_high, int addition) {
        update(0, 0, origin_data.length - 1, up_low, up_high, addition);
    }

    public static void main(String[] args) {
        int station = 5;
        int[] data = new int[station];
        for (int i =0; i < station; i++)
            data[i] = 3;
        SegmentTree st = new SegmentTree(data);

        Random rand = new Random();

        int test_num = 10000;
        for(int i=0;i<test_num;i++){
            int departure = rand.nextInt(station) + 1;
            int arrival = departure + rand.nextInt(station - departure + 1) + 1; // arrival is always greater than departure
            if (i % 3 == 0){
                st.update(departure-1, arrival-2, -1);
            }else if(i % 11 == 0){
                st.update(departure-1, arrival-2, 1);
            }else {
                st.query(departure, arrival);
            }
        }

    }
}
