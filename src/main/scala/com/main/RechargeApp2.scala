package com.main

import java.lang

import com.Utils.{JedisConnectionPool, JedisOffset, Utils_Time}
import com.alibaba.fastjson.JSON
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
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
                   Description：二、充值请求
                  Copyright(c),2019,Beijing
          This program is protected by copyright laws.
            Date:2019/08/30 0:16
                    @author Mr.Liu 
                    @version : 1.0
*/
object RechargeApp2 {
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
    val groupId = "zk001"
    // topic
    val topic = "bigdata"
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
    // 对城市数据进行处理
    val file = ssc.sparkContext.textFile("F:\\Big-Data-22\\项目\\02充值平台实时统计分析\\city.txt")
    val map = file.map(x => (x.split(" ")(0), x.split(" ")(1))).collectAsMap()
    // 将数据进行广播
    val broad = ssc.sparkContext.broadcast(map).value

    stream.foreachRDD({
      rdd =>
        //获取offset的位置、topic
        val offestRange = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
        // 业务处理
        val baseRDD= rdd.map(t => JSON.parseObject(t.value()))
          //过滤符合条件的接口（充值请求）
          .filter(json => json.getString("serviceName").equalsIgnoreCase("sendRechargeReq")
          //判断接口是否成功
          && json.getString("interFacRst").equals("0000"))
          .map(x => {
            //业务结果
            val result = x.getString("bussinessRst")
            //chargefee>shouldfee
            //订单总金额shouldfee
            val moneyCount: Double = if (result.equals("0000")) x.getDouble("shouldfee") else 0.0
            //订单的充值金额chargefee
            val money: Double = if (result.equals("0000")) x.getDouble("chargefee") else 0.0
            //成功数
            val success = if (result.equals("0000")) 1 else 0
            //请求开始时间
            val startReqTime = x.getString("startReqTime")
            //请求结束时间
            val endReqTime = x.getString("endReqTime")
            //取出省份编码
            val proCode = x.getString("provinceCode")
            //与广播变量进行匹配,拿到省份
            val province = broad.get(proCode).get

            //startReqTime.substring(0, 8), //天
            //startReqTime.substring(0,10), //小时
            //startReqTime.substring(0,12), //分钟
            (
              province,
              (province,startReqTime.substring(0,12)),
              (province,startReqTime.substring(0,10)),
              List[Double](1, money,success)
            )
          })
        //指标一 统计每个省份的充值失败数,及失败率(province,List(1, money,success))
        val res1: RDD[(String, List[Double])] = baseRDD.map(t => (t._1, t._4)).reduceByKey((list1, list2) => {
          // list1(1,2,3).zip(list2(1,2,3)) = list((1,1),(2,2),(3,3))
          // map处理内部的元素相加
          list1.zip(list2).map(t => t._1 + t._2)
        })
        JedisAPP.Result06(res1,ssc)

        //指标二 统计每分钟各省的充值笔数和充值金额
        val res2: RDD[((String, String), List[Double])] = baseRDD.map(t => (t._2, t._4)).reduceByKey((list1, list2) => {
          // list1(1,2,3).zip(list2(1,2,3)) = list((1,1),(2,2),(3,3))
          // map处理内部的元素相加
          list1.zip(list2).map(t => t._1 + t._2)
        })
//        JedisAPP.Result07(res2)

        //指标三 统计每小时各省的充值笔数和充值金额
        val res3: RDD[((String, String), List[Double])] = baseRDD.map(t => (t._3, t._4)).reduceByKey((list1, list2) => {
          // list1(1,2,3).zip(list2(1,2,3)) = list((1,1),(2,2),(3,3))
          // map处理内部的元素相加
          list1.zip(list2).map(t => t._1 + t._2)
        })
//        JedisAPP.Result08(res3)

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
