package com.example.mystore.mapper.typehandler;

import com.example.mystore.entity.vo.SpecVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class SpecVOTypeHandler extends BaseTypeHandler<List<SpecVO>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<SpecVO>> TYPE_REF = new TypeReference<List<SpecVO>>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<SpecVO> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize SpecVO list", e);
        }
    }

    @Override
    public List<SpecVO> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseList(value);
    }

    @Override
    public List<SpecVO> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseList(value);
    }

    @Override
    public List<SpecVO> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseList(value);
    }

    private List<SpecVO> parseList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse SpecVO list from JSON: " + json, e);
        }
    }
}
