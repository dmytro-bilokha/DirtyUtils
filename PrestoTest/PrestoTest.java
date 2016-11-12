import com.teradata.presto.jdbc4.DataSource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

public class PrestoTest {
    private static final String DRIVER_CLASS = "com.teradata.presto.jdbc4.Driver";
    private static final String PRESTO_PROPERTY_STRING = ";User=dmytro;LogLevel=6;LogPath=C:\\\\Dev";
    private static final String CONNECTION_URL = "jdbc:presto://bigdata04.host:9000/hive/grx" + PRESTO_PROPERTY_STRING;
    private static final String RESULT_TEMPLATE = "<result column=\"%s\" jdbcType=\"%s\" property=\"%s\" />";
    private static final String OBJ_FIELD_TEMPLATE = "private %s %s;";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Please, provide filename with query!");
            return;
        }
        String sqlQuery = null;
        try {
            sqlQuery = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            System.out.println("Got unsupported encoding exception!");
            return;
        } catch (IOException ex) {
            System.out.println("Unable to read file: " + args[0]);
            return;
        }
        Connection connection = getConnection(CONNECTION_URL);
        if (connection == null) {
            System.out.println("Connection is null, exiting");
            return;
        }
        Statement statement = getStatement(connection);
        if (statement == null) {
            System.out.println("Statement is null, exiting");
            return;
        }
        ResultSet resultSet = executeQuery(statement, sqlQuery);
        if (resultSet == null) {
            System.out.println("ResultSet is null, exiting");
            return;
        }
        StringBuilder xmlMapping = new StringBuilder();
        StringBuilder objectFields = new StringBuilder();
        try {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columns = metaData.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                String fieldName = toCamelCase(metaData.getColumnName(i));
                xmlMapping.append(String.format(RESULT_TEMPLATE
                        , metaData.getColumnName(i), metaData.getColumnTypeName(i), fieldName));
                xmlMapping.append(System.lineSeparator());
                String fieldType = null;
                switch (metaData.getColumnTypeName(i)) {
                    case "INTEGER":
                        fieldType = "Integer";
                        break;

                    case "BIGINT":
                        fieldType = "Long";
                        break;

                    case "VARCHAR":
                        fieldType = "String";
                        break;

                    default:
                        fieldType = "UKNOWN";
                }
                objectFields.append(String.format(OBJ_FIELD_TEMPLATE, fieldType, fieldName));
                objectFields.append(System.lineSeparator());
            }
        } catch (SQLException ex) {
            System.out.println("Failed to get result set metadata!");
            ex.printStackTrace();
        } finally {
            closeAll(Arrays.asList(resultSet, statement, connection));
        }
        System.out.println(xmlMapping.toString());
        System.out.println(objectFields.toString());
    }

    private static Connection getConnection(String connectionUrl) {
        Connection connection = null;
        try {
            Class.forName(DRIVER_CLASS);
            DataSource ds = new com.teradata.presto.jdbc4.DataSource();
            ds.setURL(connectionUrl);
            connection = ds.getConnection();
        } catch (ClassNotFoundException ex) {
            System.out.println("Failed to load driver class!");
            ex.printStackTrace();
        } catch (SQLException ex) {
            System.out.println("Failed to get connection from the datasource!");
            ex.printStackTrace();
        }
        return connection;
    }

    private static Statement getStatement(Connection connection) {
        Statement statement = null;
        try {
            statement = connection.createStatement();
        } catch (SQLException ex) {
            System.out.println("Failed to createStatement!");
            ex.printStackTrace();
        }
        return statement;
    }

    private static ResultSet executeQuery(Statement statement, String query) {
        ResultSet resultSet = null;
        try {
            resultSet = statement.executeQuery(query);
        } catch (SQLException ex) {
            System.out.println("Failed to execute query!");
            ex.printStackTrace();
        }
        return resultSet;
    }

    private static void closeAll(List<AutoCloseable> closeables) {
        for (AutoCloseable cl : closeables) {
            if (cl != null) {
                try {
                    cl.close();
                } catch (Exception ex) {
                    System.out.println("Exception during closing resource: " + cl);
                    ex.printStackTrace();
                }
            }
        }
    }

    private static String toCamelCase(String dbName) {
        StringBuilder resultBuilder = new StringBuilder(dbName.length());
        resultBuilder.append(Character.toLowerCase(dbName.charAt(0)));
        boolean upperFlag = false;
        for (int i = 1; i < dbName.length(); i++) {
            if (dbName.charAt(i) == '_') {
                upperFlag = true;
                continue;
            }
            resultBuilder.append(upperFlag ? Character.toUpperCase(dbName.charAt(i))
                    : Character.toLowerCase(dbName.charAt(i)));
            upperFlag = false;
        }
        return resultBuilder.toString();
    }

}
