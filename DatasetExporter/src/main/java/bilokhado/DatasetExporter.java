package bilokhado;

import com.teradata.presto.jdbc4.DataSource;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.database.QueryDataSet;
import org.dbunit.dataset.xml.FlatDtdDataSet;
import org.dbunit.dataset.xml.FlatXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


public class DatasetExporter {

    private static final Logger logger = LoggerFactory.getLogger(DatasetExporter.class);
    private static final String DRIVER_CLASS = "com.teradata.presto.jdbc4.Driver";
    private static final String PRESTO_PROPERTY_STRING = ";User=dmytro;LogLevel=6;LogPath=C:\\\\\\\\Dev";
    private static final String CONNECTION_URL = "jdbc:presto://bigdata.host:9000/hive" + PRESTO_PROPERTY_STRING;
    private static final String CREATE_TEMPLATE = "%s%s %s(%d)";
    private static final String INSERT_TEMPLATE = "%s%s";
    private static final String NEW_LINE = System.lineSeparator();
    private static final String RESULT_FILENAME_BASE = "dataset-output";

    public static void main(String[] cmdLineParams) {
        if (cmdLineParams.length != 1) {
            logger.error("Got no properties file name");
            return;
        }
        Properties datasetQueries = loadPropertiesFromFile(cmdLineParams[0]);
        if (datasetQueries == null) {
            logger.error("Properties is null");
            return;
        }
        Connection connection = getConnection(CONNECTION_URL);
        if (connection == null) {
            logger.error("Connection is null");
            return;
        }
        //retrieveAndSaveDataset(connection, datasetQueries, RESULT_FILENAME_BASE);
        exportSqlSet(connection, datasetQueries, RESULT_FILENAME_BASE);
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.error("Unable to close database connection", ex);
        }
    }

    private static Connection getConnection(String connectionUrl) {
        Connection connection = null;
        try {
            Class.forName(DRIVER_CLASS);
            DataSource ds = new com.teradata.presto.jdbc4.DataSource();
            ds.setURL(connectionUrl);
            connection = ds.getConnection();
        } catch (ClassNotFoundException ex) {
            logger.error("Failed to load driver class!", ex);
        } catch (SQLException ex) {
            logger.error("Failed to get connection from the datasource!", ex);
        }
        return connection;
    }

    private static Properties loadPropertiesFromFile(String fileName) {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(fileName));
        } catch (IOException ex) {
            logger.error("Unable to read properties file: {}", fileName, ex);
            properties = null;
        }
        return properties;
    }

    private static void exportSqlSet(Connection connection, Properties queries, String baseFileName) {
        List<String> createTablesDdl = new ArrayList<>(queries.size());
        List<String> insertDataDml = new ArrayList<>(queries.size());
        for (String tableName : queries.stringPropertyNames()) {
            Statement statement = getStatement(connection);
            if (statement == null) {
                logger.error("Statement is null for {}. Skipping the table {}"
                        , queries.getProperty(tableName), tableName);
                continue;
            }
            ResultSet resultSet = executeQuery(statement, queries.getProperty(tableName));
            if (resultSet == null) {
                logger.error("ResutlSet is null for {}. Skipping the table {}"
                        , queries.getProperty(tableName), tableName);
                continue;
            }
            try {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columns = metaData.getColumnCount();
                StringBuilder ddlBuilder = new StringBuilder();
                List<Integer> escapeColumnValue = new ArrayList<>();
                StringBuilder insertStatementBuilder = new StringBuilder();
                ddlBuilder.append("CREATE TABLE ");
                ddlBuilder.append(tableName);
                ddlBuilder.append(" (");
                ddlBuilder.append(NEW_LINE);
                insertStatementBuilder.append("INSERT INTO ");
                insertStatementBuilder.append(tableName);
                insertStatementBuilder.append(" (");
                insertStatementBuilder.append(NEW_LINE);
                for (int i = 1; i <= columns; i++) {
                    int precision = metaData.getPrecision(i);
                    precision = precision > 2048 ? 2048 : precision;
                    ddlBuilder.append(String.format(CREATE_TEMPLATE
                            , i > 1 ? "    , " : "    "
                            , metaData.getColumnName(i)
                            , metaData.getColumnTypeName(i)
                            , precision));
                    ddlBuilder.append(NEW_LINE);
                    insertStatementBuilder.append(String.format(INSERT_TEMPLATE
                            , i > 1 ? ", " : ""
                            , metaData.getColumnName(i)));
                    if (metaData.getColumnType(i) == Types.VARCHAR) {
                        escapeColumnValue.add(i);
                    }
                }
                ddlBuilder.append(");");
                insertStatementBuilder.append(")");
                insertStatementBuilder.append(NEW_LINE);
                insertStatementBuilder.append("VALUES (");
                insertDataDml.add(retrieveData(resultSet, columns, insertStatementBuilder.toString(), escapeColumnValue));
                createTablesDdl.add(ddlBuilder.toString());
            } catch (SQLException ex) {
                logger.error("Got exception during working with result set of query {}. Skipping the table {}"
                        , queries.getProperty(tableName), tableName);
                continue;
            } finally {
                closeAll(resultSet, statement);
            }
        }
        Path fileCreate = Paths.get(baseFileName + "-create.sql");
        Path fileInsert = Paths.get(baseFileName + "-insert.sql");
        try {
            Files.write(fileCreate, createTablesDdl, Charset.forName("UTF-8"));
            Files.write(fileInsert, insertDataDml, Charset.forName("UTF-8"));
        } catch (IOException ex) {
            logger.error("Unable to create output sql file", ex);
        }
    }

    private static String retrieveData(ResultSet resultSet, int columnCount, String insertTemplate, List<Integer> shouldEscape) throws SQLException {
        StringBuilder insertValuesBuilder = new StringBuilder();
        while (resultSet.next()){
            insertValuesBuilder.append(insertTemplate);
            for (int i = 1; i <= columnCount; i++) {
                String value = resultSet.getObject(i) == null ? "NULL" : resultSet.getObject(i).toString();
                if (shouldEscape.contains(i) && !"NULL".equals(value)) {
                    value = "'" + value + "'";
                }
                insertValuesBuilder.append(String.format(INSERT_TEMPLATE
                        , i > 1 ? ", " : ""
                        , value));
            }
            insertValuesBuilder.append(");");
            insertValuesBuilder.append(NEW_LINE);
        }
        return insertValuesBuilder.toString();
    }

    private static void retrieveAndSaveDataset (Connection connection, Properties queries, String baseFileName) {
        try {
            IDatabaseConnection iconnection = new DatabaseConnection(connection);
            QueryDataSet partialDataSet = new QueryDataSet(iconnection);
            for (String tableName : queries.stringPropertyNames()) {
                partialDataSet.addTable(tableName, queries.getProperty(tableName));
            }
            FlatXmlWriter datasetWriter = new FlatXmlWriter(new FileOutputStream(baseFileName + ".xml"));
            datasetWriter.write(partialDataSet);
            FlatDtdDataSet.write(partialDataSet, new FileOutputStream(baseFileName + ".dtd"));
        } catch (DatabaseUnitException ex) {
            logger.error("Exception during dataset export", ex);
        } catch (IOException ex) {
            logger.error("Unable to create output xml file", ex);
        }
    }

    private static Statement getStatement(Connection connection) {
        Statement statement = null;
        try {
            statement = connection.createStatement();
        } catch (SQLException ex) {
            logger.error("Failed to createStatement", ex);
        }
        return statement;
    }

    private static ResultSet executeQuery(Statement statement, String query) {
        ResultSet resultSet = null;
        try {
            resultSet = statement.executeQuery(query);
        } catch (SQLException ex) {
            logger.error("Failed to execute query {}.", query, ex);
        }
        return resultSet;
    }

    private static void closeAll(AutoCloseable... closeables) {
        for (AutoCloseable cl : closeables) {
            if (cl != null) {
                try {
                    cl.close();
                } catch (Exception ex) {
                    logger.error("Exception during closing resource: {}", cl, ex);
                }
            }
        }
    }

}
