package com.zuoye_0829

import java.lang

import com.Utils.{JedisConnectionPool, JedisOffset}
import com.alibaba.fastjson.JSON
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.{SparkConf}
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, HasOffsetRanges, KafkaUtils, LocationStrategies}

/*
                    .::::.
                  .::::::::.
                 :::::::::::
             ..:::::::::::'	  FUCK YOU
           '::::::::::::'		Goddess bless, never BUG
             .::::::::::
        '::::::::::::::..
             ..::::::::::::.
           ``::::::::::::::::
            ::::``:::::::::'        .:::.
           ::::'   ':::::'       .::::::::.
         .::::'      ::::     .:::::::'::::.
        .:::'       :::::  .:::::::::' ':::::.
       .::'        :::::.:::::::::'      ':::::.
      .::'         ::::::::::::::'         ``::::.
  ...:::           ::::::::::::'              ``::.
 ```` ':.          ':::::::::'                  ::::..
                    '.:::::'                    ':'````..

 ━━━━━━━━━━━━━━━━━━━━ 女神保佑,永无BUG ━━━━━━━━━━━━━━━━━━━━
                     Description：xxxx
                  Copyright(c),2019,Beijing
          This program is protected by copyright laws.
            Date:2019/08/30 9:16
                    @author Mr.Liu 
                    @version : 1.0
*/
object zuoye {
  def main(args: Array[String]): Unit = {
    //SparkCOnf属性配置
    val conf = new SparkConf().setAppName(this.getClass.getName).setMaster("local[*]")
    //RDD序列化 节约内存
    conf.set("spark.serialize", "org.apache.spark.serializer.KryoSerializer")
    //压缩RDD
    conf.set("spark.rdd.compress", "true")
    //batchSize = 分区的数量 * 采样时间 * maxRatePerPartition
    conf.set("spark.streaming.kafka.maxRatePerPartition", "10000") //拉取数据
    conf.set("spark.streaming.kafka.stopGracefullyOnShutdown", "true") //优雅的停止
    //创建SparkStreaming
    val ssc: StreamingContext = new StreamingContext(conf, Seconds(2))
    // 配置参数
    // 配置基本参数
    // 组名
    val groupId = "zk002"
    // topic
    val topic = "JsonData"
    // 指定Kafka的broker地址（SparkStreaming程序消费过程中，需要和Kafka的分区对应）
    val brokerList = "192.168.239.132:9092"
    // 编写Kafka的配置参数
    val kafkas = Map[String, Object](
      "bootstrap.servers" -> brokerList,
      // kafka的Key和values解码方式
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> groupId,
      // 从头消费
      "auto.offset.reset" -> "earliest",
      // 不需要程序自动提交Offset
      "enable.auto.commit" -> (false: lang.Boolean)
    )
    // 创建topic集合，可能会消费多个Topic
    val topics = Set(topic)
    // 第一步获取Offset
    // 第二步通过Offset获取Kafka数据
    // 第三步提交更新Offset
    // 获取Offset
    var fromOffset: Map[TopicPartition, Long] = JedisOffset(groupId)
    // 判断一下有没数据
    val stream: InputDStream[ConsumerRecord[String, String]] =
      if (fromOffset.size == 0) {
        KafkaUtils.createDirectStream(ssc,
          // 本地策略
          // 将数据均匀的分配到各个Executor上面
          LocationStrategies.PreferConsistent,
          // 消费者策略
          // 可以动态增加分区
          ConsumerStrategies.Subscribe[String, String](topics, kafkas)
        )
      } else {
        // 不是第一次消费
        KafkaUtils.createDirectStream(
          ssc,
          LocationStrategies.PreferConsistent,
          ConsumerStrategies.Assign[String, String](fromOffset.keys, kafkas, fromOffset)
        )
      }
    stream.foreachRDD({
      rdd =>
        //获取offset的位置、topic
        val offestRange = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
        // 业务处理
        val baseRDD = rdd.map(t => JSON.parseObject(t.value()))
          .map(x => {
            //获取手机号
            val phoneNum = x.getString("phoneNum")
            //获取金额
            val money: Int = x.getIntValue("money")
            //获取日期        //2018-09-13T02:15:16.054Z
            val date = x.getString("date")
            //获得月份
            val month = date.substring(0, 7).split("-")(1)
            (date.substring(0, 7), date.substring(0, 10), phoneNum, List[Double](1, money),month)
          })
        //指标1.求每天充值总金额
        val lines1: RDD[(String, List[Double])] = baseRDD.map(t => (t._2, t._4))
          .reduceByKey((list1, list2) => {
            list1.zip(list2).map(t => t._1 + t._2)
          })
        lines1.foreachPartition(f => {
          val jedis = JedisConnectionPool.getConnection()
          f.foreach(t => {
            jedis.hincrBy(t._1, "money", t._2(1).toLong)
          })
          jedis.close()
        })
        //指标2.求每月各手机号充值的平均金额 ((月份,月,手机号),List(1,money))

        baseRDD.map(t => ((t._1, t._5, t._3), t._4))
          .reduceByKey((list1,list2)=>{
          list1.zip(list2).map(t=>t._1+t._2)
        })
          .foreachPartition(f=>{
            val jedis = JedisConnectionPool.getConnection()
            f.foreach(t=>{
//              val month = t._1._2.sortBy(-_).toList
//              val m = month(0)-month(month.length-1)+1
//              jedis.set(t._1._3,(t._2(1)/m).toString)
              jedis.hincrBy(t._1._1+" "+t._1._3,"money",(t._2(1)/t._2(0)).toLong)
            })
            jedis.close()
          })

//        val baseData2 = rdd.map(_.value()).map(t=>JSON.parseObject(t))
//          .map(t=>{
//            val money:Double = t.getDouble("money")// 充值金额
//            val starttime = t.getString("date")
//            val phoneNum=t.getString("phoneNum")
//            (starttime.substring(0,10),money,starttime.substring(0,7),phoneNum)
//          }).cache()
//        // 指标一 1
//        val result1: RDD[(String, Double)] = baseData2.map(t=>(t._1,t._2)).reduceByKey(_+_)
//
//        val value: RDD[(String, Iterable[((String, String), Double)])] = baseData2.map(t=>((t._4,t._3),t._2)).reduceByKey(_+_).groupBy(_._1._1)
//        val result2: RDD[(String, Double)] = value.mapValues(x => {
//          val strings = x.toList.map(_._1._2).sortWith(_ > _)
//          val l = OdsEtl.getMonth(strings(strings.length - 1), strings(0))
//          val sum = x.toList.map(_._2).sum
//          sum/l
//        })
        // 将偏移量进行更新
        val jedis = JedisConnectionPool.getConnection()
        for (or <- offestRange) {
          jedis.hset(groupId, or.topic + "-" + or.partition, or.untilOffset.toString)
        }
        jedis.close()
    })
    // 启动
    ssc.start()
    ssc.awaitTermination()

  }
}
