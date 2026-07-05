package com.alexander.alsbanker;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    public static void setup() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(AlsBanker.get().getConfig().getString("mysql.url"));
        config.setUsername(AlsBanker.get().getConfig().getString("mysql.user"));
        config.setPassword(AlsBanker.get().getConfig().getString("mysql.password"));
        config.setMaximumPoolSize(10);
        config.addDataSourceProperty("cachePrepStmts", "true");
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void close() {
        if (dataSource != null) dataSource.close();
    }
}