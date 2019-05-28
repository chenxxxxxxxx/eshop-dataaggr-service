package com.kailaisi.eshopdataaggrservice.rabbitmq

import com.alibaba.fastjson.JSONObject
import com.kailaisi.eshopdataaggrservice.util.FastJsonUtil
import org.springframework.amqp.rabbit.annotation.RabbitHandler
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisPool
import java.util.concurrent.atomic.AtomicBoolean

/**
 *描述：数据聚合服务
 *<p/>作者：wu
 *<br/>创建时间：2019/5/21 14:29
 */
@Component
@RabbitListener(queues = arrayOf("high-priority-aggr-data-change-queue"))
class HighPriorityDataAggrQueueReceive {
    @Autowired
    lateinit var jedisPool: JedisPool

    @RabbitHandler
    fun process(msg: String) {
        println("接收到高优先级聚合消息$msg")
        var bean = FastJsonUtil.json2Bean(msg, AggrDataChange::class.java)
        when (bean.dim_type) {
            DataType.BRAND -> processBrandDimDataChange(bean)
            DataType.CATEGORY -> processCategoryDimDataChange(bean)
            DataType.PRODUCT_DESC -> processProductDescDimDataChange(bean)
            DataType.PRODUCT -> processProductDimDataChange(bean)
        }
    }

    /**
     * 商品信息
     */
    private fun processProductDimDataChange(bean: AggrDataChange) {
        val jedis = jedisPool.resource
        val productDataJSON = jedis.get("product_${bean.id}")
        if (productDataJSON.isNullOrEmpty()) {
            jedis.del("dim_product_${bean.id}")
        } else {
            val productObject = JSONObject.parseObject(productDataJSON)
            val specification = jedis.get("product_specification_${bean.id}")
            if (!specification.isNullOrEmpty()) {
                productObject["product_specification"] = JSONObject.parseObject(specification)
            }
            val property = jedis.get("product_property_${bean.id}")
            if (!property.isNullOrEmpty()) {
                productObject["product_property"] = JSONObject.parseObject(property)
            }
            jedis.set("dim_product_${bean.id}", JSONObject.toJSONString(productObject))
        }
        jedis.close()
    }

    /**
     * 商品介绍
     */
    private fun processProductDescDimDataChange(bean: AggrDataChange) {
        var resource = jedisPool.resource
        val info = resource.get("product_desc_${bean.id}")
        if (info.isNullOrEmpty()) {
            resource.del("dim_product_desc_${bean.id}")
        } else {
            resource.set("dim_product_desc_${bean.id}", info)
        }
        resource.close()
    }

    /**
     * 分类数据
     */
    private fun processCategoryDimDataChange(bean: AggrDataChange) {
        var jedis = jedisPool.resource
        try {
            val info = jedis.get("category_${bean.id}")
            if (info.isNullOrEmpty()) {
                jedis.del("dim_category_${bean.id}")
            } else {
                jedis.set("dim_category_${bean.id}", info)
            }
        } finally {
            jedis.close()
        }
    }

    /**
     * 品牌数据聚合，此处主要由于是业务简化了。实际是需要根据不同的数据来源，来组合成不同的数据。
     */
    private fun processBrandDimDataChange(bean: AggrDataChange) {
        var resource = jedisPool.resource
        val info = resource.get("brand_${bean.id}")
        if (info.isNullOrEmpty()) {
            resource.del("dim_brand_${bean.id}")
        } else {
            resource.set("dim_brand_${bean.id}", info)
        }
        resource.close()
    }
}