package com.zuoye_0829

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
            Date:2019/08/30 21:31
                    @author Mr.Liu 
                    @version : 1.0
*/

import java.text.SimpleDateFormat

import com.alibaba.fastjson.JSON
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import redis.clients.jedis.Jedis

object OdsEtl {
  def getDate(requestId:String,endTime:String)={
    val startTime = requestId.substring(0,17)
    //val format = FastDateFormat.getInstance("yyyyMMddHHmmssSSS")

    val format = new SimpleDateFormat("yyyyMMddHHmmssSSS")
    format.parse(endTime).getTime - format.parse(startTime).getTime

  }

  def getMonth(maxTime:String,minTime:String)={
    //val startTime = requestId.substring(0,17)
    //val format = FastDateFormat.getInstance("yyyyMMddHHmmssSSS")

    val format = new SimpleDateFormat("yyyy-MM")
    (format.parse(maxTime).getYear - format.parse(minTime).getYear)*12+ (format.parse(maxTime).getMonth - format.parse(minTime).getMonth)+1
  }

  def odsEtl(rdd:RDD[String],broad:Broadcast[Map[String, String]]) ={
    val odsdata = rdd.map(str => JSON.parseObject(str))
      //过滤出需要的数据
      .filter(x => x.getString("serviceName").equals("reChargeNotifyReq")&&
      x.getString("interFacRst").equals("0000"))
      .map(rdd => {
        //事物结果
        val result = rdd.getString("bussinessRst")//充值结果
        //获得充值金额
        val fee = rdd.getString("chargefee").toDouble
        //获取省份
        val provinceCode = rdd.getString("provinceCode")

        val province = broad.value.get(provinceCode).get // 根据省份编码取到省份名字

        //获取充值得发起时间和结束时间
        val requestId = rdd.getString("requestId")
        //获取日期
        val date = requestId.substring(0, 8)
        //小时
        val hour = requestId.substring(8, 10)
        //分钟
        val minute = requestId.substring(10, 12)
        //充值结束的时间
        val receiveTime = rdd.getString("receiveNotifyTime")

        val time = getDate(requestId, receiveTime)
        val SuccedResult: (Int, Double, Long) = if (result.equals("0000")) (1, fee, time) else (0, 0, 0)


        (date, hour,minute,List[Double](1, SuccedResult._1, SuccedResult._2, SuccedResult._3), province,(province,date,hour))

      }).cache()
    odsdata
  }

}
