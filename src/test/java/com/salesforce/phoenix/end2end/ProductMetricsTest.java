/*******************************************************************************
 * Copyright (c) 2013, Salesforce.com, Inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *     Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     Neither the name of Salesforce.com nor the names of its contributors may 
 *     be used to endorse or promote products derived from this software without 
 *     specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package com.salesforce.phoenix.end2end;

import static com.salesforce.phoenix.util.TestUtil.PHOENIX_JDBC_URL;
import static com.salesforce.phoenix.util.TestUtil.TEST_PROPERTIES;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.text.Format;
import java.text.ParseException;
import java.util.*;

import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.salesforce.phoenix.query.QueryConstants;
import com.salesforce.phoenix.schema.PDataType;
import com.salesforce.phoenix.util.*;

public class ProductMetricsTest extends BaseClientMangedTimeTest {
    private static Format format = DateUtil.getDateParser(DateUtil.DEFAULT_DATE_FORMAT);
    private static final String PRODUCT_METRICS_NAME = "PRODUCT_METRICS";
    private static final String DS1 = "1970-01-01 00:58:00";
    private static final String DS2 = "1970-01-01 01:02:00";
    private static final String DS3 = "1970-01-01 01:30:00";
    private static final String DS4 = "1970-01-01 01:45:00";
    private static final String DS5 = "1970-01-01 02:00:00";
    private static final String DS6 = "1970-01-01 04:00:00";
    private static final Date D1 = toDate(DS1);
    private static final Date D2 = toDate(DS2);
    private static final Date D3 = toDate(DS3);
    private static final Date D4 = toDate(DS4);
    private static final Date D5 = toDate(DS5);
    private static final Date D6 = toDate(DS6);
    private static final Object ROUND_1HR = toDate("1970-01-01 01:00:00");
    private static final Object ROUND_2HR = toDate("1970-01-01 02:00:00");
    private static final String F1 = "A";
    private static final String F2 = "B";
    private static final String F3 = "C";
    private static final String R1 = "R1";
    private static final String R2 = "R2";
    
    private static byte[][] getSplits(String tenantId) {
        return new byte[][] { 
            ByteUtil.concat(Bytes.toBytes(tenantId), PDataType.DATE.toBytes(D3)),
            ByteUtil.concat(Bytes.toBytes(tenantId), PDataType.DATE.toBytes(D5)),
            };
    }
    
    private static Date toDate(String dateString) {
        try {
            return (Date)format.parseObject(dateString);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void initTable(byte[][] splits, long ts) throws Exception {
        ensureTableCreated(getUrl(),PRODUCT_METRICS_NAME,splits, ts-2);
    }

    private static void assertNoRows(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("select 1 from PRODUCT_METRICS");
        assertFalse(rs.next());
    }
    
    private static void initTableValues(String tenantId, byte[][] splits, long ts) throws Exception {
        initTable(splits, ts);

        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + ts; // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            assertNoRows(conn);
            initTableValues(conn, tenantId);
            conn.commit();
        } finally {
            conn.close();
        }
    }
    
    protected static void initTableValues(Connection conn, String tenantId) throws Exception {
        PreparedStatement stmt = conn.prepareStatement(
            "upsert into " +
            "PRODUCT_METRICS(" +
            "    ORGANIZATION_ID, " +
            "    DATE, " +
            "    FEATURE, " +
            "    UNIQUE_USERS, " +
            "    TRANSACTIONS, " +
            "    CPU_UTILIZATION, " +
            "    DB_UTILIZATION, " +
            "    REGION, " +
            "    IO_TIME)" +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        stmt.setString(1, tenantId);
        stmt.setDate(2, D1);
        stmt.setString(3, F1);
        stmt.setInt(4, 10);
        stmt.setLong(5, 100L);
        stmt.setBigDecimal(6, BigDecimal.valueOf(0.5));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.2));
        stmt.setString(8, R2);
        stmt.setNull(9, Types.BIGINT);
        stmt.execute();
        
        stmt.setString(1, tenantId);
        stmt.setDate(2, D2);
        stmt.setString(3, F1);
        stmt.setInt(4, 20);
        stmt.setLong(5, 200);
        stmt.setBigDecimal(6, BigDecimal.valueOf(1.0));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.4));
        stmt.setString(8, null);
        stmt.setLong(9, 2000);
        stmt.execute();
        
        stmt.setString(1, tenantId);
        stmt.setDate(2, D3);
        stmt.setString(3, F1);
        stmt.setInt(4, 30);
        stmt.setLong(5, 300);
        stmt.setBigDecimal(6, BigDecimal.valueOf(2.5));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.6));
        stmt.setString(8, R1);
        stmt.setNull(9, Types.BIGINT);
        stmt.execute();
        
        stmt.setString(1, tenantId);
        stmt.setDate(2, D4);
        stmt.setString(3, F2);
        stmt.setInt(4, 40);
        stmt.setLong(5, 400);
        stmt.setBigDecimal(6, BigDecimal.valueOf(3.0));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.8));
        stmt.setString(8, R1);
        stmt.setLong(9, 4000);
        stmt.execute();
        
        stmt.setString(1, tenantId);
        stmt.setDate(2, D5);
        stmt.setString(3, F3);
        stmt.setInt(4, 50);
        stmt.setLong(5, 500);
        stmt.setBigDecimal(6, BigDecimal.valueOf(3.5));
        stmt.setBigDecimal(7, BigDecimal.valueOf(1.2));
        stmt.setString(8, R2);
        stmt.setLong(9, 5000);
        stmt.execute();
        
        stmt.setString(1, tenantId);
        stmt.setDate(2, D6);
        stmt.setString(3, F1);
        stmt.setInt(4, 60);
        stmt.setLong(5, 600);
        stmt.setBigDecimal(6, BigDecimal.valueOf(4.0));
        stmt.setBigDecimal(7, BigDecimal.valueOf(1.4));
        stmt.setString(8, null);
        stmt.setNull(9, Types.BIGINT);
        stmt.execute();
    }
        
    private static void initDateTableValues(String tenantId, byte[][] splits, long ts, Date startDate) throws Exception {
        initDateTableValues(tenantId, splits, ts, startDate, 2.0);
    }
    
    private static void initDateTableValues(String tenantId, byte[][] splits, long ts, Date startDate, double dateIncrement) throws Exception {
        initTable(splits, ts);

        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + ts; // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            assertNoRows(conn);
            initDateTableValues(conn, tenantId, startDate, dateIncrement);
            conn.commit();
        } finally {
            conn.close();
        }
    }
    
    private static void initDateTableValues(Connection conn, String tenantId, Date startDate, double dateIncrement) throws Exception {
        PreparedStatement stmt = conn.prepareStatement(
            "upsert into " +
            "PRODUCT_METRICS(" +
            "    ORGANIZATION_ID, " +
            "    DATE, " +
            "    FEATURE, " +
            "    UNIQUE_USERS, " +
            "    TRANSACTIONS, " +
            "    CPU_UTILIZATION, " +
            "    DB_UTILIZATION, " +
            "    REGION, " +
            "    IO_TIME)" +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        stmt.setString(1, tenantId);
        stmt.setDate(2, startDate);
        stmt.setString(3, "A");
        stmt.setInt(4, 10);
        stmt.setLong(5, 100L);
        stmt.setBigDecimal(6, BigDecimal.valueOf(0.5));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.2));
        stmt.setString(8, R2);
        stmt.setNull(9, Types.BIGINT);
        stmt.execute();
        
        startDate = new Date(startDate.getTime() + (long)(QueryConstants.MILLIS_IN_DAY * dateIncrement));
        stmt.setString(1, tenantId);
        stmt.setDate(2, startDate);
        stmt.setString(3, "B");
        stmt.setInt(4, 20);
        stmt.setLong(5, 200);
        stmt.setBigDecimal(6, BigDecimal.valueOf(1.0));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.4));
        stmt.setString(8, null);
        stmt.setLong(9, 2000);
        stmt.execute();
        
        startDate = new Date(startDate.getTime() + (long)(QueryConstants.MILLIS_IN_DAY * dateIncrement));
        stmt.setString(1, tenantId);
        stmt.setDate(2, startDate);
        stmt.setString(3, "C");
        stmt.setInt(4, 30);
        stmt.setLong(5, 300);
        stmt.setBigDecimal(6, BigDecimal.valueOf(2.5));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.6));
        stmt.setString(8, R1);
        stmt.setNull(9, Types.BIGINT);
        stmt.execute();
        
        startDate = new Date(startDate.getTime() + (long)(QueryConstants.MILLIS_IN_DAY * dateIncrement));
        stmt.setString(1, tenantId);
        stmt.setDate(2, startDate);
        stmt.setString(3, "D");
        stmt.setInt(4, 40);
        stmt.setLong(5, 400);
        stmt.setBigDecimal(6, BigDecimal.valueOf(3.0));
        stmt.setBigDecimal(7, BigDecimal.valueOf(0.8));
        stmt.setString(8, R1);
        stmt.setLong(9, 4000);
        stmt.execute();
        
        startDate = new Date(startDate.getTime() + (long)(QueryConstants.MILLIS_IN_DAY * dateIncrement));
        stmt.setString(1, tenantId);
        stmt.setDate(2, startDate);
        stmt.setString(3, "E");
        stmt.setInt(4, 50);
        stmt.setLong(5, 500);
        stmt.setBigDecimal(6, BigDecimal.valueOf(3.5));
        stmt.setBigDecimal(7, BigDecimal.valueOf(1.2));
        stmt.setString(8, R2);
        stmt.setLong(9, 5000);
        stmt.execute();
        
        startDate = new Date(startDate.getTime() + (long)(QueryConstants.MILLIS_IN_DAY * dateIncrement));
        stmt.setString(1, tenantId);
        stmt.setDate(2, startDate);
        stmt.setString(3, "F");
        stmt.setInt(4, 60);
        stmt.setLong(5, 600);
        stmt.setBigDecimal(6, BigDecimal.valueOf(4.0));
        stmt.setBigDecimal(7, BigDecimal.valueOf(1.4));
        stmt.setString(8, null);
        stmt.setNull(9, Types.BIGINT);
        stmt.execute();
    }
    
    @Test
    public void testDateRangeAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(1), feature FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?) GROUP BY feature";
        //String query = "SELECT count(1), feature FROM PRODUCT_METRICS GROUP BY feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getLong(1));
            assertEquals(F1, rs.getString(2));
            assertTrue(rs.next());
            assertEquals(1, rs.getLong(1));
            assertEquals(F2, rs.getString(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testPartiallyEvaluableAnd() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT date FROM PRODUCT_METRICS WHERE organization_id=? AND unique_users >= 30 AND transactions >= 300 AND cpu_utilization > 2 AND db_utilization > 0.5 AND io_time = 4000";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(D4, rs.getDate(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testPartiallyEvaluableOr() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT date FROM PRODUCT_METRICS WHERE organization_id=? AND (transactions = 10000 OR unset_column = 5 OR io_time = 4000)";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(D4, rs.getDate(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testConstantTrueHaving() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(1), feature FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?) GROUP BY feature HAVING 1=1";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getLong(1));
            assertEquals(F1, rs.getString(2));
            assertTrue(rs.next());
            assertEquals(1, rs.getLong(1));
            assertEquals(F2, rs.getString(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testConstantFalseHaving() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(1), feature FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?) GROUP BY feature HAVING 1=1 and 0=1";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, DS4);
            ResultSet rs = statement.executeQuery();
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateRangeHavingAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(1), feature FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?) GROUP BY feature HAVING count(1) >= 2";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getLong(1));
            assertEquals(F1, rs.getString(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateRangeSumLongAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(transactions), feature FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?) GROUP BY feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(500, rs.getLong(1));
            assertEquals(F1, rs.getString(2));
            assertTrue(rs.next());
            assertEquals(400, rs.getLong(1));
            assertEquals(F2, rs.getString(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testRoundAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT round(date,'hour',1),count(1) FROM PRODUCT_METRICS WHERE organization_id=? GROUP BY round(date,'hour',1)";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            Date d;
            int c;
            ResultSet rs = statement.executeQuery();
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(1 * 60 * 60 * 1000, d.getTime()); // Date bucketed into 1 hr
            assertEquals(2, c);
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(2 * 60 * 60 * 1000, d.getTime()); // Date bucketed into 2 hr
            assertEquals(3, c);
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(4 * 60 * 60 * 1000, d.getTime()); // Date bucketed into 4 hr
            assertEquals(1, c);
            
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testRoundScan() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT round(date,'hour') FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            Date d;
            long t;
            ResultSet rs = statement.executeQuery();
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            t = 1 * 60 * 60 * 1000;
            assertEquals(t, d.getTime()); // Date bucketed into 1 hr
            assertTrue(rs.next());
            assertEquals(t, d.getTime()); // Date bucketed into 1 hr
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            t = 2 * 60 * 60 * 1000;
            assertEquals(t, d.getTime()); // Date bucketed into 2 hr
            assertTrue(rs.next());
            assertEquals(t, d.getTime()); // Date bucketed into 2 hr
            assertTrue(rs.next());
            assertEquals(t, d.getTime()); // Date bucketed into 2 hr
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            t = 4 * 60 * 60 * 1000;
            assertEquals(t, d.getTime()); // Date bucketed into 4 hr
            
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testTruncAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT trunc(date,'hour'),count(1) FROM PRODUCT_METRICS WHERE organization_id=? GROUP BY trunc(date,'hour')";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            Date d;
            int c;
            ResultSet rs = statement.executeQuery();
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(0, d.getTime()); // Date bucketed into 0 hr
            assertEquals(1, c);
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(1 * 60 * 60 * 1000, d.getTime()); // Date bucketed into 1 hr
            assertEquals(3, c);
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(2 * 60 * 60 * 1000, d.getTime()); // Date bucketed into 2 hr
            assertEquals(1, c);
            
            assertTrue(rs.next());
            d = rs.getDate(1);
            c = rs.getInt(2);
            assertEquals(4 * 60 * 60 * 1000, d.getTime()); // Date bucketed into 4 hr
            assertEquals(1, c);

            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,sum(unique_users) FROM PRODUCT_METRICS WHERE organization_id=? AND transactions > 0 GROUP BY feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertTrue(rs.next());
            assertTrue(rs.next());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testHavingAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,sum(unique_users) FROM PRODUCT_METRICS WHERE organization_id=? AND transactions > 0 GROUP BY feature HAVING feature=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F1);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testConstantSumAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(1),sum(unique_users) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(6,rs.getInt(1));
            assertEquals(210,rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testMultiDimAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,region,sum(unique_users) FROM PRODUCT_METRICS WHERE organization_id=? GROUP BY feature,region";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            
            assertTrue(rs.next());
            assertEquals(F1,rs.getString(1));
            assertEquals(null,rs.getString(2));
            assertEquals(80,rs.getInt(3));
            assertTrue(rs.next());
            assertEquals(F1,rs.getString(1));
            assertEquals(R1,rs.getString(2));
            assertEquals(30,rs.getInt(3));
            assertTrue(rs.next());
            assertEquals(F1,rs.getString(1));
            assertEquals(R2,rs.getString(2));
            assertEquals(10,rs.getInt(3));

            assertTrue(rs.next());
            assertEquals(F2,rs.getString(1));
            assertEquals(R1,rs.getString(2));
            assertEquals(40,rs.getInt(3));
            
            assertTrue(rs.next());
            assertEquals(F3,rs.getString(1));
            assertEquals(R2,rs.getString(2));
            assertEquals(50,rs.getInt(3));
            
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testMultiDimRoundAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT round(date,'hour',1),feature,sum(unique_users) FROM PRODUCT_METRICS WHERE organization_id=? GROUP BY round(date,'hour',1),feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            
            Date bucket1 = new Date(1 * 60 * 60 * 1000);
            Date bucket2 = new Date(2 * 60 * 60 * 1000);
            Date bucket3 = new Date(4 * 60 * 60 * 1000);

            assertTrue(rs.next());
            assertEquals(bucket1, rs.getDate(1));
            assertEquals(F1,rs.getString(2));
            assertEquals(30,rs.getInt(3));
            
            
            assertTrue(rs.next());
            assertEquals(bucket2, rs.getDate(1));
            assertEquals(F1,rs.getString(2));
            assertEquals(30,rs.getInt(3));
            
            assertTrue(rs.next());
            assertEquals(bucket2.getTime(), rs.getDate(1).getTime());
            assertEquals(F2,rs.getString(2));
            assertEquals(40,rs.getInt(3));
            
            assertTrue(rs.next());
            assertEquals(bucket2, rs.getDate(1));
            assertEquals(F3,rs.getString(2));
            assertEquals(50,rs.getInt(3));
            
            
            assertTrue(rs.next());
            assertEquals(bucket3, rs.getDate(1));
            assertEquals(F1,rs.getString(2));
            assertEquals(60,rs.getInt(3));
            
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateRangeSumNumberUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(cpu_utilization) FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?)";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(BigDecimal.valueOf(6.5), rs.getBigDecimal(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testSumUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(unique_users),sum(cpu_utilization),sum(transactions),sum(db_utilization),sum(response_time) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(210, rs.getInt(1));
            assertEquals(BigDecimal.valueOf(14.5), rs.getBigDecimal(2));
            assertEquals(2100L, rs.getLong(3));
            assertEquals(BigDecimal.valueOf(4.6), rs.getBigDecimal(4));
            assertEquals(0, rs.getLong(5));
            assertEquals(true, rs.wasNull());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testResetColumnInSameTxn() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(transactions) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        String upsertUrl = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + ts; // Run updates at timestamp 0
        Connection upsertConn = DriverManager.getConnection(upsertUrl, props);
        try {
            initTable(getSplits(tenantId), ts);
            initTableValues(upsertConn, tenantId);
            PreparedStatement stmt = upsertConn.prepareStatement(
                    "upsert into " +
                    "PRODUCT_METRICS(" +
                    "    ORGANIZATION_ID, " +
                    "    DATE, " +
                    "    FEATURE, " +
                    "    UNIQUE_USERS," +
                    "    TRANSACTIONS) " +
                    "VALUES (?, ?, ?, ?, ?)");
            stmt.setString(1, tenantId);
            stmt.setDate(2, D1);
            stmt.setString(3, F1);
            stmt.setInt(4, 10);
            stmt.setInt(5, 200); // Change TRANSACTIONS from 100 to 200
            stmt.execute();
            upsertConn.commit();

            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(2200, rs.getInt(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testSumUngroupedHavingAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(unique_users),sum(cpu_utilization),sum(transactions),sum(db_utilization),sum(response_time) FROM PRODUCT_METRICS WHERE organization_id=? HAVING sum(unique_users) > 200 AND sum(db_utilization) > 4.5";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(210, rs.getInt(1));
            assertEquals(BigDecimal.valueOf(14.5), rs.getBigDecimal(2));
            assertEquals(2100L, rs.getLong(3));
            assertEquals(BigDecimal.valueOf(4.6), rs.getBigDecimal(4));
            assertEquals(0, rs.getLong(5));
            assertEquals(true, rs.wasNull());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testSumUngroupedHavingAggregation2() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(unique_users),sum(cpu_utilization),sum(transactions),sum(db_utilization),sum(response_time) FROM PRODUCT_METRICS WHERE organization_id=? HAVING sum(unique_users) > 200 AND sum(db_utilization) > 5";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testMinUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT min(unique_users),min(cpu_utilization),min(transactions),min(db_utilization),min('X'),min(response_time) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(10, rs.getInt(1));
            assertEquals(BigDecimal.valueOf(0.5), rs.getBigDecimal(2));
            assertEquals(100L, rs.getLong(3));
            assertEquals(BigDecimal.valueOf(0.2), rs.getBigDecimal(4));
            assertEquals("X", rs.getString(5));            
            assertEquals(0, rs.getLong(6));
            assertEquals(true, rs.wasNull());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testMinUngroupedAggregation1() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT min(cpu_utilization) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(BigDecimal.valueOf(0.5), rs.getBigDecimal(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testMaxUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT max(unique_users),max(cpu_utilization),max(transactions),max(db_utilization),max('X'),max(response_time) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(60, rs.getInt(1));
            assertEquals(BigDecimal.valueOf(4), rs.getBigDecimal(2));
            assertEquals(600L, rs.getLong(3));
            assertEquals(BigDecimal.valueOf(1.4), rs.getBigDecimal(4));
            assertEquals("X", rs.getString(5));            
            assertEquals(0, rs.getLong(6));
            assertEquals(true, rs.wasNull());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testMaxGroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,max(transactions) FROM PRODUCT_METRICS WHERE organization_id=? GROUP BY feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F1,rs.getString(1));
            assertEquals(600,rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F2,rs.getString(1));
            assertEquals(400,rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F3,rs.getString(1));
            assertEquals(500,rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testCountUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(1) FROM PRODUCT_METRICS";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(6, rs.getLong(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testCountColumnUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(io_time),sum(io_time),avg(io_time) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(3, rs.getLong(1));
            assertEquals(11000, rs.getLong(2));
            // Scale is automatically capped at 4 if no scale is specified. 
            assertEquals(new BigDecimal("3666.6666"), rs.getBigDecimal(3));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testNoRowsUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(io_time),sum(io_time),avg(io_time),count(1) FROM PRODUCT_METRICS WHERE organization_id=? AND feature > ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2,F3);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getLong(1));
            assertFalse(rs.wasNull());
            assertEquals(0, rs.getLong(2));
            assertTrue(rs.wasNull());
            assertEquals(null, rs.getBigDecimal(3));
            assertEquals(0, rs.getLong(4));
            assertFalse(rs.wasNull());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testAvgUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT avg(unique_users) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(BigDecimal.valueOf(35), rs.getBigDecimal(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testAvgUngroupedAggregationOnValueField() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT AVG(DB_UTILIZATION) FROM PRODUCT_METRICS WHERE organization_id=?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);

            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            // The column is defined as decimal(31,10), so the value is capped at 10 decimal points.
            assertEquals(new BigDecimal("0.7666666666"), rs.getBigDecimal(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    /**
     * Test aggregate query with rownum limit that does not explicity contain a count(1) as a select expression
     * @throws Exception
     */
    @Test
    public void testLimitSumUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        // No count(1) aggregation, so it will get added automatically
        String query = "SELECT sum(unique_users),sum(cpu_utilization),sum(transactions),sum(db_utilization),sum(response_time) feature FROM PRODUCT_METRICS WHERE organization_id=? LIMIT 3";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(60, rs.getInt(1));
            assertEquals(BigDecimal.valueOf(4), rs.getBigDecimal(2));
            assertEquals(600L, rs.getLong(3));
            assertEquals(BigDecimal.valueOf(1.2), rs.getBigDecimal(4));
            assertEquals(0, rs.getLong(5));
            assertEquals(true, rs.wasNull());
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    /**
     * Test grouped aggregation query with a mix of aggregated data types
     * @throws Exception
     */
    @Test
    public void testSumGroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,sum(unique_users),sum(cpu_utilization),sum(transactions),sum(db_utilization),sum(response_time),count(1) c FROM PRODUCT_METRICS WHERE organization_id=? AND feature < ? GROUP BY feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F3);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F1, rs.getString("feature"));
            assertEquals(120, rs.getInt("sum(unique_users)"));
            assertEquals(BigDecimal.valueOf(8), rs.getBigDecimal(3));
            assertEquals(1200L, rs.getLong(4));
            assertEquals(BigDecimal.valueOf(2.6), rs.getBigDecimal(5));
            assertEquals(0, rs.getLong(6));
            assertEquals(true, rs.wasNull());
            assertEquals(4, rs.getLong("c"));
            assertTrue(rs.next());
            assertEquals(F2, rs.getString("feature"));
            assertEquals(40, rs.getInt(2));
            assertEquals(BigDecimal.valueOf(3), rs.getBigDecimal(3));
            assertEquals(400L, rs.getLong(4));
            assertEquals(BigDecimal.valueOf(0.8), rs.getBigDecimal(5));
            assertEquals(0, rs.getLong(6));
            assertEquals(true, rs.wasNull());
            assertEquals(1, rs.getLong("c"));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testDegenerateAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT count(1), feature FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND date <= to_date(?) GROUP BY feature";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            // Start date larger than end date
            statement.setString(2, DS4);
            statement.setString(3, DS2);
            ResultSet rs = statement.executeQuery();
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    /**
     * Query with multiple > expressions on continquous PK columns
     * @throws Exception
     */
    @Test
    public void testFeatureDateRangeAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,unique_users FROM PRODUCT_METRICS WHERE organization_id=? AND date >= to_date(?) AND feature > ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, DS2);
            statement.setString(3, F2);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F3, rs.getString(1));
            assertEquals(50, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    /**
     * Query with non contiguous PK column expressions (i.e. no expresion for DATE)
     * @throws Exception
     */
    @Test
    public void testFeatureGTAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,unique_users FROM PRODUCT_METRICS WHERE organization_id=? AND feature > ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F2);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F3, rs.getString(1));
            assertEquals(50, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testFeatureGTEAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,unique_users FROM PRODUCT_METRICS WHERE organization_id=? AND feature >= ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F2);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F2, rs.getString(1));
            assertEquals(40, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F3, rs.getString(1));
            assertEquals(50, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testFeatureEQAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,unique_users FROM PRODUCT_METRICS WHERE organization_id=? AND feature = ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F2);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F2, rs.getString(1));
            assertEquals(40, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    
    @Test
    public void testFeatureLTAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,unique_users FROM PRODUCT_METRICS WHERE organization_id=? AND feature < ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F2);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(10, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(20, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(30, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(60, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testFeatureLTEAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,unique_users FROM PRODUCT_METRICS WHERE organization_id=? AND feature <= ?";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setString(2, F2);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(10, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(20, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(30, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F2, rs.getString(1));
            assertEquals(40, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(F1, rs.getString(1));
            assertEquals(60, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    @Test
    public void testOrderByNonAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        initTableValues(tenantId, getSplits(tenantId), ts);
        String query = "SELECT date FROM PRODUCT_METRICS WHERE organization_id=? AND unique_users <= 30 ORDER BY transactions DESC LIMIT 10";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);

        Connection conn = DriverManager.getConnection(url, props);
        try {
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(D3, rs.getDate(1));
            assertTrue(rs.next());
            assertEquals(D2, rs.getDate(1));
            assertTrue(rs.next());
            assertEquals(D1, rs.getDate(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testOrderByUngroupedAggregation() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(unique_users),count(feature) " +
                       "FROM PRODUCT_METRICS " + 
                       "WHERE organization_id=? " +
                       "ORDER BY sum(unique_users)";

        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(210, rs.getInt(1));
            assertEquals(6, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testOrderByGroupedAggregation() throws Exception {        
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature,sum(unique_users),count(feature) " +
                       "FROM PRODUCT_METRICS " + 
                       "WHERE organization_id=? " +
                       "GROUP BY feature,round(date,'hour',1) " +
                       "ORDER BY 1 desc,feature desc,round(date,'hour',1),feature,sum(unique_users)";

        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            
            Object[][] expected = {
                    {F3, 50, 1},
                    {F2, 40, 1},
                    {F1, 30, 2},
                    {F1, 30, 1},
                    {F1, 60, 1},
            };

            for (int i = 0; i < expected.length; i++) {
                assertTrue(rs.next());
                assertEquals(expected[i][0], rs.getString(1));
                assertEquals(expected[i][1], rs.getInt(2));
                assertEquals(expected[i][2], rs.getInt(3));
            }
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testOrderByUnprojectedOrderingColumn() throws Exception {        
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT sum(unique_users) " +
                       "FROM PRODUCT_METRICS " + 
                       "WHERE organization_id=? " +
                       "GROUP BY feature " +
                       "ORDER BY count(feature),feature";

        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            
            int[] expected = {40, 50, 120};
            for (int i = 0; i < expected.length; i++) {
                assertTrue(rs.next());
                assertEquals(expected[i], rs.getInt(1));
            }
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testOrderByNullColumns__nullsFirst() throws Exception {
        helpTestOrderByNullColumns(true);
    }
    
    @Test
    public void testOrderByNullColumns__nullsLast() throws Exception {
        helpTestOrderByNullColumns(false);
    }
    
    private void helpTestOrderByNullColumns(boolean nullsFirst) throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT region " +
                       "FROM PRODUCT_METRICS " + 
                       "WHERE organization_id=? " +
                       "GROUP BY region " +
                       "ORDER BY region nulls " + (nullsFirst ? "first" : "last");

        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            
            List<String> expected = Lists.newArrayList(null, R1, R2);
            Ordering<String> regionOrdering = Ordering.natural();
            regionOrdering = nullsFirst ? regionOrdering.nullsFirst() : regionOrdering.nullsLast();
            Collections.sort(expected, regionOrdering);
            
            for (String region : expected) {
                assertTrue(rs.next());
                assertEquals(region, rs.getString(1));
            }
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }

    /**
     * Test to repro ArrayIndexOutOfBoundException that happens during filtering in BinarySubsetComparator
     * only after a flush is performed
     * @throws Exception
     */
    @Test
    public void testFilterOnTrailingKeyColumn() throws Exception {
    	long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        Properties props = new Properties(TEST_PROPERTIES);
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(ts+1));
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_URL, props);

        HBaseAdmin admin = null;
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            admin = new HBaseAdmin(driver.getQueryServices().getConfig());
            admin.flush(SchemaUtil.getTableName(Bytes.toBytes(PRODUCT_METRICS_NAME)));
            String query = "SELECT SUM(TRANSACTIONS) FROM " + PRODUCT_METRICS_NAME + " WHERE FEATURE=?";
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, F1);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(1200, rs.getInt(1));
        } finally {
            if (admin != null) admin.close();
            conn.close();
        }	
    }
    
    @Test
    public void testFilterOnTrailingKeyColumn2() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT organization_id, date, feature FROM PRODUCT_METRICS WHERE substr(organization_id,1,3)=? AND date > to_date(?)";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, null/*getSplits(tenantId)*/, ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId.substring(0,3));
            statement.setString(2, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(tenantId, rs.getString(1));
            assertEquals(D5.getTime(), rs.getDate(2).getTime());
            assertEquals(F3, rs.getString(3));
            assertTrue(rs.next());
            assertEquals(tenantId, rs.getString(1));
            assertEquals(D6, rs.getDate(2));
            assertEquals(F1, rs.getString(3));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testSubstringNotEqual() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT organization_id, date, feature FROM PRODUCT_METRICS WHERE organization_id=? AND date > to_date(?)";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId.substring(0,3));
            statement.setString(2, DS4);
            ResultSet rs = statement.executeQuery();
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testKeyOrderedAggregation1() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT date, sum(UNIQUE_USERS) FROM PRODUCT_METRICS WHERE date > to_date(?) GROUP BY organization_id, date";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(D5, rs.getDate(1));
            assertEquals(50, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(D6, rs.getDate(1));
            assertEquals(60, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testKeyOrderedAggregation2() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT date, sum(UNIQUE_USERS) FROM PRODUCT_METRICS WHERE date < to_date(?) GROUP BY organization_id, date";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(D1, rs.getDate(1));
            assertEquals(10, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(D2, rs.getDate(1));
            assertEquals(20, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(D3, rs.getDate(1));
            assertEquals(30, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testKeyOrderedRoundAggregation1() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT round(date,'HOUR'), sum(UNIQUE_USERS) FROM PRODUCT_METRICS WHERE date < to_date(?) GROUP BY organization_id, round(date,'HOUR')";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(ROUND_1HR, rs.getDate(1));
            assertEquals(30, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(ROUND_2HR, rs.getDate(1));
            assertEquals(30, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testKeyOrderedRoundAggregation2() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT round(date,'HOUR'), sum(UNIQUE_USERS) FROM PRODUCT_METRICS WHERE date <= to_date(?) GROUP BY organization_id, round(date,'HOUR')";
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            initTableValues(tenantId, getSplits(tenantId), ts);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, DS4);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals(ROUND_1HR, rs.getDate(1));
            assertEquals(30, rs.getInt(2));
            assertTrue(rs.next());
            assertEquals(ROUND_2HR, rs.getDate(1));
            assertEquals(70, rs.getInt(2));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateSubtractionCompareNumber() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and ? - date > 3"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = new Date(System.currentTimeMillis());
            Date endDate = new Date(startDate.getTime() + 6 * QueryConstants.MILLIS_IN_DAY);
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setDate(2, endDate);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("A", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("B", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateSubtractionLongToDecimalCompareNumber() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and ? - date - 1.5 > 3"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = new Date(System.currentTimeMillis());
            Date endDate = new Date(startDate.getTime() + 9 * QueryConstants.MILLIS_IN_DAY);
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setDate(2, endDate);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("A", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("B", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("C", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateSubtractionCompareDate() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and date - 1 >= ?"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = new Date(System.currentTimeMillis());
            Date endDate = new Date(startDate.getTime() + 9 * QueryConstants.MILLIS_IN_DAY);
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setDate(2, endDate);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("F", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testDateAddCompareDate() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and date + 1 >= ?"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = new Date(System.currentTimeMillis());
            Date endDate = new Date(startDate.getTime() + 8 * QueryConstants.MILLIS_IN_DAY);
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setDate(2, endDate);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("E", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("F", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testCurrentDate() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and date - current_date() > 8"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = new Date(System.currentTimeMillis());
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("F", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testCurrentTime() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and date - current_time() > 8"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = new Date(System.currentTimeMillis());
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("F", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
    
    @Test
    public void testTruncateNotTraversableToFormScanKey() throws Exception {
        long ts = nextTimestamp();
        String tenantId = getOrganizationId();
        String query = "SELECT feature FROM PRODUCT_METRICS WHERE organization_id = ? and TRUNC(date,'DAY') <= ?"; 
        String url = PHOENIX_JDBC_URL + ";" + PhoenixRuntime.CURRENT_SCN_ATTRIB + "=" + (ts + 5); // Run query at timestamp 5
        Properties props = new Properties(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(url, props);
        try {
            Date startDate = toDate("2013-01-01 00:00:00");
            initDateTableValues(tenantId, getSplits(tenantId), ts, startDate, 0.5);
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, tenantId);
            statement.setDate(2, new Date(startDate.getTime() + (long)(QueryConstants.MILLIS_IN_DAY * 0.25)));
            ResultSet rs = statement.executeQuery();
            assertTrue(rs.next());
            assertEquals("A", rs.getString(1));
            assertTrue(rs.next());
            assertEquals("B", rs.getString(1));
            assertFalse(rs.next());
        } finally {
            conn.close();
        }
    }
}
