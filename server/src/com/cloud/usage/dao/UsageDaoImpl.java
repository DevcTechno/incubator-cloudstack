// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.usage.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.ejb.Local;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.exception.UsageServerException;
import com.cloud.usage.UsageVO;
import com.cloud.user.AccountVO;
import com.cloud.user.UserStatisticsVO;
import com.cloud.utils.DateUtil;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;

@Component
@Local(value={UsageDao.class})
public class UsageDaoImpl extends GenericDaoBase<UsageVO, Long> implements UsageDao {
	public static final Logger s_logger = Logger.getLogger(UsageDaoImpl.class.getName());
	private static final String DELETE_ALL = "DELETE FROM cloud_usage";
	private static final String DELETE_ALL_BY_ACCOUNTID = "DELETE FROM cloud_usage WHERE account_id = ?";
	    private static final String INSERT_ACCOUNT = "INSERT INTO cloud_usage.account (id, account_name, type, domain_id, removed, cleanup_needed) VALUES (?,?,?,?,?,?)";
    private static final String INSERT_USER_STATS = "INSERT INTO cloud_usage.user_statistics (id, data_center_id, account_id, public_ip_address, device_id, device_type, network_id, net_bytes_received," +
    												" net_bytes_sent, current_bytes_received, current_bytes_sent, agg_bytes_received, agg_bytes_sent) VALUES (?,?,?,?,?,?,?,?,?,?, ?, ?, ?)";

    private static final String UPDATE_ACCOUNT = "UPDATE cloud_usage.account SET account_name=?, removed=? WHERE id=?";
    private static final String UPDATE_USER_STATS = "UPDATE cloud_usage.user_statistics SET net_bytes_received=?, net_bytes_sent=?, current_bytes_received=?, current_bytes_sent=?, agg_bytes_received=?, agg_bytes_sent=? WHERE id=?";

    private static final String GET_LAST_ACCOUNT = "SELECT id FROM cloud_usage.account ORDER BY id DESC LIMIT 1";
    private static final String GET_LAST_USER_STATS = "SELECT id FROM cloud_usage.user_statistics ORDER BY id DESC LIMIT 1";
    private static final String GET_PUBLIC_TEMPLATES_BY_ACCOUNTID = "SELECT id FROM cloud.vm_template WHERE account_id = ? AND public = '1' AND removed IS NULL";

    protected final static TimeZone s_gmtTimeZone = TimeZone.getTimeZone("GMT");

    public UsageDaoImpl () {}

	public void deleteRecordsForAccount(Long accountId) {
	    String sql = ((accountId == null) ? DELETE_ALL : DELETE_ALL_BY_ACCOUNTID);
        Transaction txn = Transaction.open(Transaction.USAGE_DB);
        PreparedStatement pstmt = null;
        try {
            txn.start();
            pstmt = txn.prepareAutoCloseStatement(sql);
            if (accountId != null) {
                pstmt.setLong(1, accountId.longValue());
            }
            pstmt.executeUpdate();
            txn.commit();
        } catch (Exception ex) {
        	txn.rollback();
            s_logger.error("error retrieving usage vm instances for account id: " + accountId);
        } finally {
            txn.close();
        }
	}

	@Override
	public List<UsageVO> searchAllRecords(SearchCriteria<UsageVO> sc, Filter filter) {
	    return listIncludingRemovedBy(sc, filter);
	}

	@Override
	public void saveAccounts(List<AccountVO> accounts) throws UsageServerException {
	    Transaction txn = Transaction.currentTxn();
	    try {
	        txn.start();
	        String sql = INSERT_ACCOUNT;
	        PreparedStatement pstmt = null;
	        pstmt = txn.prepareAutoCloseStatement(sql); // in reality I just want CLOUD_USAGE dataSource connection
	        for (AccountVO acct : accounts) {
	            pstmt.setLong(1, acct.getId());
	            pstmt.setString(2, acct.getAccountName());
	            pstmt.setShort(3, acct.getType());
	            pstmt.setLong(4, acct.getDomainId());

	            Date removed = acct.getRemoved();
	            if (removed == null) {
	                pstmt.setString(5, null);
	            } else {
	                pstmt.setString(5, DateUtil.getDateDisplayString(TimeZone.getTimeZone("GMT"), acct.getRemoved()));
	            }

	            pstmt.setBoolean(6, acct.getNeedsCleanup());

	            pstmt.addBatch();
	        }
	        pstmt.executeBatch();
	        txn.commit();
	    } catch (Exception ex) {
	        txn.rollback();
	        s_logger.error("error saving account to cloud_usage db", ex);
	        throw new UsageServerException(ex.getMessage());
	    }
	}

    @Override
    public void updateAccounts(List<AccountVO> accounts) throws UsageServerException {
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            String sql = UPDATE_ACCOUNT;
            PreparedStatement pstmt = null;
            pstmt = txn.prepareAutoCloseStatement(sql); // in reality I just want CLOUD_USAGE dataSource connection
            for (AccountVO acct : accounts) {
                pstmt.setString(1, acct.getAccountName());

                Date removed = acct.getRemoved();
                if (removed == null) {
                    pstmt.setString(2, null);
                } else {
                    pstmt.setString(2, DateUtil.getDateDisplayString(TimeZone.getTimeZone("GMT"), acct.getRemoved()));
                }

                pstmt.setLong(3, acct.getId());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            txn.commit();
        } catch (Exception ex) {
            txn.rollback();
            s_logger.error("error saving account to cloud_usage db", ex);
            throw new UsageServerException(ex.getMessage());
        }
    }

	@Override
	public void saveUserStats(List<UserStatisticsVO> userStats) throws UsageServerException {
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            String sql = INSERT_USER_STATS;
            PreparedStatement pstmt = null;
            pstmt = txn.prepareAutoCloseStatement(sql); // in reality I just want CLOUD_USAGE dataSource connection
            for (UserStatisticsVO userStat : userStats) {
                pstmt.setLong(1, userStat.getId());
                pstmt.setLong(2, userStat.getDataCenterId());
                pstmt.setLong(3, userStat.getAccountId());
                pstmt.setString(4, userStat.getPublicIpAddress());
                if(userStat.getDeviceId() != null){
                    pstmt.setLong(5, userStat.getDeviceId());
                } else {
                    pstmt.setNull(5, Types.BIGINT);
                }
                pstmt.setString(6, userStat.getDeviceType());
                if(userStat.getNetworkId() != null){
                    pstmt.setLong(7, userStat.getNetworkId());
                } else {
                    pstmt.setNull(7, Types.BIGINT);
                }
                pstmt.setLong(8, userStat.getNetBytesReceived());
                pstmt.setLong(9, userStat.getNetBytesSent());
                pstmt.setLong(10, userStat.getCurrentBytesReceived());
                pstmt.setLong(11, userStat.getCurrentBytesSent());
                pstmt.setLong(12, userStat.getAggBytesReceived());
                pstmt.setLong(13, userStat.getAggBytesSent());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            txn.commit();
        } catch (Exception ex) {
            txn.rollback();
            s_logger.error("error saving user stats to cloud_usage db", ex);
            throw new UsageServerException(ex.getMessage());
        }
	}

    @Override
    public void updateUserStats(List<UserStatisticsVO> userStats) throws UsageServerException {
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            String sql = UPDATE_USER_STATS;
            PreparedStatement pstmt = null;
            pstmt = txn.prepareAutoCloseStatement(sql);  // in reality I just want CLOUD_USAGE dataSource connection
            for (UserStatisticsVO userStat : userStats) {
                pstmt.setLong(1, userStat.getNetBytesReceived());
                pstmt.setLong(2, userStat.getNetBytesSent());
                pstmt.setLong(3, userStat.getCurrentBytesReceived());
                pstmt.setLong(4, userStat.getCurrentBytesSent());
                pstmt.setLong(5, userStat.getAggBytesReceived());
                pstmt.setLong(6, userStat.getAggBytesSent());
                pstmt.setLong(7, userStat.getId());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            txn.commit();
        } catch (Exception ex) {
            txn.rollback();
            s_logger.error("error saving user stats to cloud_usage db", ex);
            throw new UsageServerException(ex.getMessage());
        }
    }

	@Override
    public Long getLastAccountId() {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        String sql = GET_LAST_ACCOUNT;
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Long.valueOf(rs.getLong(1));
            }
        } catch (Exception ex) {
            s_logger.error("error getting last account id", ex);
        }
        return null;
    }

    @Override
    public Long getLastUserStatsId() {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        String sql = GET_LAST_USER_STATS;
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Long.valueOf(rs.getLong(1));
            }
        } catch (Exception ex) {
            s_logger.error("error getting last user stats id", ex);
        }
        return null;
    }
    
    @Override
    public List<Long> listPublicTemplatesByAccount(long accountId) {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        String sql = GET_PUBLIC_TEMPLATES_BY_ACCOUNTID;
        List<Long> templateList = new ArrayList<Long>();
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setLong(1, accountId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                templateList.add(Long.valueOf(rs.getLong(1)));
            }
        } catch (Exception ex) {
            s_logger.error("error listing public templates", ex);
        }
        return templateList;
    }
}
