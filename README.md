# 车票存储数据结构
###### 概论
*   车厢号对于本系统而言没有太大的意义，通过 车厢数 X 每节车厢座位数 = 总座位数
*   暂定每列火车车票数据存放到一个线性表中
*   目前可以想到三种结构
    1)  使用二维数组存储站点区间的余票
    2)  使用一维数组存储每一段站点间的余票
    3)  使用字典存储任意站点间的余票
*   最后决定使用AtomicInteger表示站点座位是否被卖出

###### 查询操作分析
* 目标：查询某route，从departure站出发（即为D站），到arrival站(记为A站)的余票 
1.  使用二维数组进行查询，可以直接获取某route的数据值`tickets[D][A]`
2.  使用一维数组，需要获取`min(tickets[D:A])`，即D到A所有站点段中最小值；可以构造堆，加快获取最小的速度
3.  使用字典，直接获取站间余票`ticket(hash(D+A))`
4.  使用AtomicInteger每次查询都需要遍历统计，可以从上次买票的位置开始查起提高效率

###### 买票操作分析
*   目标： 购买某route，从D站出发到A站的车票，如果有余票则购买成功，否则购买失败
1.  使用二维数组，先检查余票；在购买车票使得`tickets[D][A]`减一时，所有相关的区间都要减一；
即所有与（D，A）相交不为空的区间都要减一
2.  使用一维数组，先检查余票（区间内任意一个为零都会导致无法售票）；
然后对`tickets[D:A]`每一个数据依次减一
3.  使用字典，和二维数组差不多
4.  使用AtomicInteger的compareAndSet效果最好

###### 退票操作分析
*   目标：退还某route，从D站出发到A站的车票，一定能退还成功
1.  使用二维数组，和区间（D，A）相关都要加一
2.  使用一位数组，恢复区间内所有站点的票数，加一
3.  使用字典和二维数组差不多


### 并发竞争分析
1.  买票间会产生竞争
2.  退票间会产生竞争
3.  买票和退票间会产生竞争
4.  查询应该没有竞争或者弱竞争（需要保持静态的正确性），那就是需要snapshot？
5.  竞争最细粒度为站之间的每个最小区间

###### 注
+   线段树存余票统计存在问题，需要加锁，反而使吞吐量下降；最后采取Atomic系列类型的原子操作，实现了无锁化，大幅提高了吞吐量

###### IDEA
*   记录购票和退票请求，判断是否有可以优化的地方
*   购票类似于pop，退票类似于push
*   最优细化，将锁加在发生冲突的每个最小区间
*   先把这个版本的性能测出来，作为基准
*   也要实现有关于分配座位的功能吗？
*   当前的系统可能会导致显示有余票但是购票失败，这是因为查询余票并不论座位分配，
而座位分配则额外要求行程中必须始终占有同一座位（该条件有可能不满足）。
