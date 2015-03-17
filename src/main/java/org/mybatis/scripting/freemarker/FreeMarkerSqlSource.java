package org.mybatis.scripting.freemarker;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies provided parameter(s) to FreeMarker template.
 * Then passes the result into default MyBatis engine (and it finally replaces #{}-params to '?'-params).
 * So, FreeMarker is used as preprocessor for MyBatis engine.
 *
 * @author elwood
 */
public class FreeMarkerSqlSource implements SqlSource {
    private final Template template;
    private final Configuration configuration;

    public FreeMarkerSqlSource(Template template, Configuration configuration) {
        this.template = template;
        this.configuration = configuration;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        // Add to passed parameterObject our predefined directive - MyBatisParamDirective
        // It will be available as "p" inside templates
        Object dataContext;
        ArrayList additionalParams = new ArrayList();
        if (parameterObject != null) {
            if (parameterObject instanceof Map) {
                HashMap<String, Object> map = new HashMap<>((Map<String, Object>) parameterObject);
                map.put(MyBatisParamDirective.DEFAULT_KEY, new MyBatisParamDirective());
                map.put("__additional_params__", additionalParams);
                dataContext = map;
            } else {
                dataContext = new ParamObjectAdapter(parameterObject, additionalParams);
            }
        } else {
            HashMap<Object, Object> map = new HashMap<>();
            map.put(MyBatisParamDirective.DEFAULT_KEY, new MyBatisParamDirective());
            map.put("__additional_params__", additionalParams);
            dataContext = map;
        }

        CharArrayWriter writer = new CharArrayWriter();
        try {
            template.process(dataContext, writer);
        } catch (TemplateException | IOException e) {
            throw new RuntimeException(e);
        }

        // We got SQL ready for MyBatis here. This SQL contains params declarations like "#{param}",
        // they will be replaced to '?' by MyBatis engine further
        String sql = writer.toString();

        if (!additionalParams.isEmpty()) {
            if (!(parameterObject instanceof Map)) {
                throw new UnsupportedOperationException("Auto-generated prepared statements parameters" +
                        " are not available if using parameters object. Use @Param-annotated parameters instead.");
            }

            Map<String, Object> parametersMap = (Map<String, Object>) parameterObject;
            for (int i = 0; i < additionalParams.size(); i++) {
                parametersMap.put("_p" + i, additionalParams.get(i));
            }
        }

        // Pass retrieved SQL into MyBatis engine, it will substitute prepared-statements parameters
        SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
        Class<?> parameterType1 = parameterObject == null ? Object.class : parameterObject.getClass();
        SqlSource sqlSource = sqlSourceParser.parse(sql, parameterType1, new HashMap<String, Object>());
        return sqlSource.getBoundSql(parameterObject);
    }
}
