package com.main

import com.Utils.{ConnectPoolUtils, JedisConnectionPool}
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContext

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
                  Copyright(c),2019,Beijing
          This program is protected by copyright laws.
            Date:2019/08/29 16:27
                    @author Mr.Liu
                    @version : 1.0
*/
//指标统计
object JedisAPP {
  //---------------一、充值通知--------------------
  //指标一 1)统计全网的充值订单量, 充值金额, 充值成功数,充值总时长
  def Result01(lines: RDD[(String, List[Double])]) = {
    lines.foreachPartition(f => {
      val jedis = JedisConnectionPool.getConnection()
      f.foreach(t => {
        //按天统计
        // 充值订单数
        jedis.hincrBy(t._1, "total", t._2(0).toLong)
        //充值金额
        jedis.hincrBy(t._1, "money", t._2(1).toLong)
        // 充值成功数
        jedis.hincrByFloat(t._1, "success", t._2(2))
        // 充值总时长
        jedis.hincrBy(t._1, "costtime", t._2(3).toLong)
      })
      jedis.close()
    })
  }

  //指标一 2)统计全网每分钟的订单量数据
  def Result02(lines: RDD[(String, Double)]) = {
    lines.foreachPartition(f => {
      val jedis = JedisConnectionPool.getConnection()
      f.foreach(t => {
        jedis.hincrBy(t._1, "count_Minutes", t._2.toLong)
      })
      jedis.close()
    })
  }

  //指标二 2.1统计每小时各个省份的充值失败数据量
  def Result03(lines: RDD[((String, String), List[Double])]) = {
    lines.foreachPartition(f => {
      //获取mysql连接
      val conn = ConnectPoolUtils.getConnections()
      //处理数据
      f.foreach(t => {
        val sql = "insert into proHour(Pro,Hour,counts)" +
          "values('" + t._1._1 + "','" + t._1._2 + "'," + (t._2(0) - t._2(2)) + ")"
        val statement = conn.createStatement()
        statement.executeUpdate(sql)
      })
      //释放连接
      ConnectPoolUtils.resultConn(conn)
    })
  }

  //指标三 充值订单省份 TOP10
  //以省份为维度统计订单量排名前 10的省份数据,并且统计每个省份的订单成功率
  def Result04(lines: RDD[(String, List[Double])], ssc: StreamingContext) = {
    // 先进行排序，按照订单量进行排序
    val sorted = lines.sortBy(_._2(0), false)
    // 进行百分比求值（成功率）
    val value: Array[(String, Double, Int)] = sorted.map(t => {
      (t._1, t._2(2) / t._2(0).toInt, t._2(0).toInt)
    }).take(10)
    // 创建新的RDD
    val rdd = ssc.sparkContext.makeRDD(value)
    // 循环每个分区
    rdd.foreachPartition(f => {
      //获取mysql连接
      val conn = ConnectPoolUtils.getConnections()
      //处理数据
      f.foreach(t => {
        val sql = "insert into successTop10(province,success,counts)" +
          "values('" + t._1 + "','" + t._2 + "','" + t._3 + "')"
        val state = conn.createStatement()
        state.executeUpdate(sql)
      })
      //释放连接
      ConnectPoolUtils.resultConn(conn)
    })
  }

  //指标四 实时统计每小时的充值笔数和充值金额。
  def Result05(lines: RDD[(String, List[Double])], ssc: StreamingContext) = {
    lines.foreachPartition(f => {
      //获取mysql连接
      val conn = ConnectPoolUtils.getConnections()
      //处理数据
      f.foreach(t => {
        val sql = "insert into Result05(hour,counts,money)" +
          "values('" + t._1 + "','" + t._2(0) + "','" + t._2(1) + "')"
        val state = conn.createStatement()
        state.executeUpdate(sql)
      })
      //释放连接
      ConnectPoolUtils.resultConn(conn)
    })
  }

  //----------------二、充值请求-----------------------
  //指标五 业务失败省份 TOP3（离线处理[每天]）（存入MySQL）
  //以省份为维度统计每个省份的充值失败数,及失败率存入MySQL中
  //(province,List(1, money,success))
  def Result06(lines: RDD[(String, List[Double])], ssc: StreamingContext) = {
    // 先进行排序，按照订单量进行排序
    val sorted = lines.sortBy(_._2(0), false)
    // 进行百分比求值（失败率）
    val value = lines.map(t => {
      (t._1, t._2(0) - t._2(2), ((t._2(0) - t._2(2)) / t._2(0)).toDouble)
    }).sortBy(_._2, false).take(3)
    // 创建新的RDD
    val rdd = ssc.sparkContext.makeRDD(value)
    // 循环每个分区
    rdd.foreachPartition(f => {
      //获取mysql连接
      val conn = ConnectPoolUtils.getConnections()
      //处理数据
      f.foreach(t => {
        val sql = "insert into Result06(province,failes,mortality)" +
          "values('" + t._1 + "','" + t._2 + "','" + t._3 + "')"
        val state = conn.createStatement()
        state.executeUpdate(sql)
      })
      //释放连接
      ConnectPoolUtils.resultConn(conn)
    })
  }

  //指标六 充值机构分布
  //1)	以省份为维度,统计每分钟各省的充值笔数和充值金额
  def Result07(lines: RDD[((String, String), List[Double])]) = {
    lines.foreachPartition(f => {
      //获取mysql连接
      val conn = ConnectPoolUtils.getConnections()
      //处理数据
      f.foreach(t => {
        val sql = "insert into Result07(minute,province,count,money)" +
          "values('" + t._1._2 + "','" + t._1._1 + "','" + t._2(0) + "','" + t._2(1) + "')"
        val state = conn.createStatement()
        state.executeUpdate(sql)
      })
      //释放连接
      ConnectPoolUtils.resultConn(conn)
    })
  }

  //2)	以省份为维度,统计每小时各省的充值笔数和充值金额((province,startReqTime),List(1, money,success))
  def Result08(lines: RDD[((String, String), List[Double])]) = {
    lines.foreachPartition(f => {
      //获取mysql连接
      val conn = ConnectPoolUtils.getConnections()
      //处理数据
      f.foreach(t => {
        val sql = "insert into Result08(hour,province,count,money)" +
          "values('" + t._1._2 + "','" + t._1._1 + "','" + t._2(0) + "','" + t._2(1) + "')"
        val state = conn.createStatement()
        state.executeUpdate(sql)
      })
      //释放连接
      ConnectPoolUtils.resultConn(conn)
    })
  }
}
