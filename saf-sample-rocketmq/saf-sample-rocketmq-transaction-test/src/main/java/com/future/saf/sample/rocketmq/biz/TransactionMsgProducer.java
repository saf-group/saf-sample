/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.future.saf.sample.rocketmq.biz;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.future.saf.sample.rocketmq.biz.entity.TransferRecord;
import com.future.saf.sample.rocketmq.common.Constant;

@Component
public class TransactionMsgProducer implements InitializingBean {
	private static TransactionMQProducer producer = new TransactionMQProducer("please_rename_unique_group_name");

	@Resource
	private TransactionListenerImpl transactionListener;

	@Override
	public void afterPropertiesSet() throws Exception {
		producer.setNamesrvAddr(Constant.Rocketmq_Namesrv);

		ExecutorService executorService = new ThreadPoolExecutor(2, 5, 100, TimeUnit.SECONDS,
				new ArrayBlockingQueue<Runnable>(2000), new ThreadFactory() {
					@Override
					public Thread newThread(Runnable r) {
						Thread thread = new Thread(r);
						thread.setName("client-transaction-msg-check-thread");
						return thread;
					}
				});

		producer.setExecutorService(executorService);
		// 设置回调检查监听器
		producer.setTransactionListener(transactionListener);
		try {
			producer.start();
		} catch (MQClientException e) {
			e.printStackTrace();
		}

		new Thread(new Runnable() {

			@Override
			public void run() {

				while (true) {
					// 单次转账唯一编号
					String businessNo = UUID.randomUUID().toString();

					// 要发送的事务消息 设置转账人 被转账人 转账金额
					TransferRecord transferRecord = new TransferRecord();
					transferRecord.setFromUserId(1L);
					transferRecord.setToUserId(2L);
					transferRecord.setChangeMoney(100L);
					transferRecord.setRecordNo(businessNo);

					try {
						Message msg = new Message(Constant.TransactionTopic, "tag", businessNo,
								JSON.toJSONString(transferRecord).getBytes(RemotingHelper.DEFAULT_CHARSET));
						SendResult sendResult = producer.sendMessageInTransaction(msg, null);
						System.out.println("prepare事务消息发送结果:" + sendResult.getSendStatus());
					} catch (Exception e) {
						e.printStackTrace();
					}

					try {
						Thread.sleep(1000l);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			}
		}).start();
	}

}
