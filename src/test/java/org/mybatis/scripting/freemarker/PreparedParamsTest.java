/**
 *    Copyright 2015-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.scripting.freemarker;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Test of using FreeMarker to generate prepared statements parameters.
 *
 * @author elwood
 */
public class PreparedParamsTest {
  protected static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  public static void setUp() throws Exception {
    Class.forName("org.hsqldb.jdbcDriver");

    JDBCDataSource dataSource = new JDBCDataSource();
    dataSource.setUrl("jdbc:hsqldb:mem:db3");
    dataSource.setUser("sa");
    dataSource.setPassword("");

    try (Connection conn = dataSource.getConnection()) {
      try (Reader reader = Resources.getResourceAsReader("org/mybatis/scripting/freemarker/create-db.sql")) {
        ScriptRunner runner = new ScriptRunner(conn);
        runner.setLogWriter(null);
        runner.setErrorLogWriter(null);
        runner.runScript(reader);
        conn.commit();
      }
    }

    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    Environment environment = new Environment("development", transactionFactory, dataSource);

    // You can call configuration.setDefaultScriptingLanguage(FreeMarkerLanguageDriver.class)
    // after this to use FreeMarker driver by default.
    Configuration configuration = new Configuration(environment);

    configuration.addMapper(PreparedParamsMapper.class);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
  }

  @Test
  public void testInCall() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      PreparedParamsMapper mapper = sqlSession.getMapper(PreparedParamsMapper.class);
      List<Name> names = mapper.findByNames(new ArrayList<String>() {
        private static final long serialVersionUID = 1L;
        {
          add("Pebbles");
          add("Barney");
          add("Betty");
        }
      });
      Assertions.assertTrue(names.size() == 3);
    }
  }

  /**
   * PersistenceException will be thrown with cause of UnsupportedOperationException
   */
  @Test
  public void testParamsObjectCall() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      final PreparedParamsMapper mapper = sqlSession.getMapper(PreparedParamsMapper.class);
      Assertions.assertThrows(PersistenceException.class, () -> {
        mapper.findUsingParamsObject(new PreparedParam());
      });
    }
  }

  @Test
  public void testNoParamsCall() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      PreparedParamsMapper mapper = sqlSession.getMapper(PreparedParamsMapper.class);
      Name name = mapper.findUsingParams(new PreparedParam.InnerClass());
      Assertions.assertTrue(name != null && name.getFirstName().equals("Wilma"));
    }
  }
}
