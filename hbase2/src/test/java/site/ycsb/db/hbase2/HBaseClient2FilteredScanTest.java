package site.ycsb.db.hbase2;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import site.ycsb.ByteIterator;
import site.ycsb.measurements.Measurements;
import site.ycsb.workloads.CoreWorkload;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static site.ycsb.workloads.CoreWorkload.TABLENAME_PROPERTY;
import static site.ycsb.workloads.CoreWorkload.TABLENAME_PROPERTY_DEFAULT;

public class HBaseClient2FilteredScanTest {

  private final static String COLUMN_FAMILY = "cf";

  private static HBaseTestingUtility testingUtil;
  private HBaseClient2 client;
  private Table table = null;
  private String tableName;

  private static boolean isWindows() {
    final String os = System.getProperty("os.name");
    return os.startsWith("Windows");
  }

  /**
   * Creates a mini-cluster for use in these tests.
   *
   * This is a heavy-weight operation, so invoked only once for the test class.
   */
  @BeforeClass
  public static void setUpClass() throws Exception {
    // Minicluster setup fails on Windows with an UnsatisfiedLinkError.
    // Skip if windows.
    assumeTrue(!isWindows());
    testingUtil = HBaseTestingUtility.createLocalHTU();
    testingUtil.startMiniCluster();
  }

  /**
   * Tears down mini-cluster.
   */
  @AfterClass
  public static void tearDownClass() throws Exception {
    if (testingUtil != null) {
      testingUtil.shutdownMiniCluster();
    }
  }

  final class HBase2ClientWithConstantValueFilter extends HBaseClient2{
    private byte[] valueFilter;

    HBase2ClientWithConstantValueFilter(byte[] valueFilter) {
      this.valueFilter = valueFilter;
    }

    @Override
    protected byte[] getRandomFilterValue(){
      return valueFilter;
    }

  }

  /**
   * Sets up the mini-cluster for testing.
   *
   * We re-create the table for each test.
   */

  public void setUp(Properties p, byte[] randomFilteringValue) throws Exception {
    p.setProperty("columnfamily", COLUMN_FAMILY);
    client = new HBase2ClientWithConstantValueFilter(randomFilteringValue);
    client.setConfiguration(new Configuration(testingUtil.getConfiguration()));

    Measurements.setProperties(p);
    final CoreWorkload workload = new CoreWorkload();
    workload.init(p);

    tableName = p.getProperty(TABLENAME_PROPERTY, TABLENAME_PROPERTY_DEFAULT);
    table = testingUtil.createTable(TableName.valueOf(tableName), Bytes.toBytes(COLUMN_FAMILY));

    client.setProperties(p);
    client.init();
  }

  @After
  public void tearDown() throws Exception {
    table.close();
    testingUtil.deleteTable(TableName.valueOf(tableName));
  }


  @Test
  public void testScan() throws Exception {
    Properties properties=new Properties();
    setUp(properties, Bytes.toBytes(5));
    // Fill with data
    final String colStr = "row_number";
    final byte[] col = Bytes.toBytes(colStr);
    final int n = 15;
    final List<Put> puts = new ArrayList<Put>(n);
    for(int i = 0; i < n; i++) {
      final byte[] key = Bytes.toBytes(String.format("%05d", i));
      final byte[] value = java.nio.ByteBuffer.allocate(4).putInt(i).array();
      final Put p = new Put(key);
      p.addColumn(Bytes.toBytes(COLUMN_FAMILY), col, value);
      puts.add(p);
    }
    table.put(puts);

    // Test
    final Vector<HashMap<String, ByteIterator>> result =
        new Vector<HashMap<String, ByteIterator>>();

    // Scan 5 records, skipping the first
    client.scan(tableName, "00001", 10, new HashSet<>(Arrays.asList("row_number")), result);

    assertEquals(10, result.size());
    for(int i = 0; i < 5; i++) {
      final HashMap<String, ByteIterator> row = result.get(i);
      assertEquals(1, row.size());
      assertTrue(row.containsKey(colStr));
      final byte[] bytes = row.get(colStr).toArray();
      final ByteBuffer buf = ByteBuffer.wrap(bytes);
      final int rowNum = buf.getInt();
      assertEquals(i + 1, rowNum);
    }
  }

  @Test
  public void testScanWithProperty() throws Exception {
    Properties properties=new Properties();
    properties.setProperty("hbase.usescanvaluefiltering", String.valueOf(true));
    properties.setProperty("hbase.scanfilteroperation","less");
    properties.setProperty("debug",String.valueOf(true));

    setUp(properties, Bytes.toBytes(5));
    // Fill with data
    final String colStr = "row_number";
    final byte[] col = Bytes.toBytes(colStr);
    final int n = 15;
    final List<Put> puts = new ArrayList<Put>(n);
    for(int i = 0; i < n; i++) {
      final byte[] key = Bytes.toBytes(String.format("%05d", i));
      final byte[] value = java.nio.ByteBuffer.allocate(4).putInt(i).array();
      final Put p = new Put(key);
      p.addColumn(Bytes.toBytes(COLUMN_FAMILY), col, value);
      puts.add(p);
    }
    table.put(puts);

    // Test
    final Vector<HashMap<String, ByteIterator>> result =
        new Vector<HashMap<String, ByteIterator>>();

    // Scan 5 records, skipping the first
    client.scan(tableName, "00001", 5, new HashSet<>(Arrays.asList("row_number")), result);
    int expected = 4;
    assertEquals(expected, result.size());
    for(int i = 0; i < expected; i++) {
      final HashMap<String, ByteIterator> row = result.get(i);
      assertEquals(1, row.size());
      assertTrue(row.containsKey(colStr));
      final byte[] bytes = row.get(colStr).toArray();
      final ByteBuffer buf = ByteBuffer.wrap(bytes);
      final int rowNum = buf.getInt();
      assertEquals(i + 1, rowNum);
    }
  }

}
