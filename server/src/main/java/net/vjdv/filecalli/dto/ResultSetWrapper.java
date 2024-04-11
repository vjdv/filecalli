package net.vjdv.filecalli.dto;

import net.vjdv.filecalli.exceptions.DataException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Wrapper for ResultSet to handle exceptions
 */
public class ResultSetWrapper {

    private final ResultSet rs;

    public ResultSetWrapper(ResultSet rs) {
        this.rs = rs;
    }

    public boolean next() {
        try {
            return rs.next();
        } catch (SQLException ex) {
            throw new DataException("Error getting next result", ex);
        }
    }

    public int getInt(String column) {
        try {
            return rs.getInt(column);
        } catch (SQLException ex) {
            throw new DataException("Error getting int from column " + column, ex);
        }
    }

    public int getInt(int column) {
        try {
            return rs.getInt(column);
        } catch (SQLException ex) {
            throw new DataException("Error getting int from column " + column, ex);
        }
    }

    public String getString(String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            throw new DataException("Error getting string from column " + column, ex);
        }
    }

    public String getString(int column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            throw new DataException("Error getting string from column " + column, ex);
        }
    }

}
