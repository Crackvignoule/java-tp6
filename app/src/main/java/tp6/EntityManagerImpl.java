// Author : Killian PAVY
// TODO Clean & Refactor the code to avoid code duplication (especially for the SQL queries)
// TODO Add comments
// TODO Try to get rid of all the unused method of EntityManager
// NOTE: I tried to use abstract to avoid implementing all the methods but it did not work because we need to instanciate the class in the tests

package tp6;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.StoredProcedureQuery;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.metamodel.Metamodel;

public class EntityManagerImpl implements EntityManager {

    private static final String DB_URL = "jdbc:hsqldb:mem:mymemdb";
    private static final String DB_USER = "SA";
    private static final String DB_PASSWORD = "";
    private static final Map<Class<?>, String> SQL_TYPE_MAP = new HashMap<>();

    static {
        SQL_TYPE_MAP.put(int.class, "INT");
        SQL_TYPE_MAP.put(Integer.class, "INT");
        SQL_TYPE_MAP.put(long.class, "BIGINT");
        SQL_TYPE_MAP.put(Long.class, "BIGINT");
        SQL_TYPE_MAP.put(double.class, "DOUBLE");
        SQL_TYPE_MAP.put(Double.class, "DOUBLE");
        SQL_TYPE_MAP.put(String.class, "VARCHAR(255)");
    }

    private String getSqlType(Class<?> type) {
        return SQL_TYPE_MAP.get(type);
    }

    private Field getAccessibleField(Class<?> entityClass, String fieldName) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private String processFields(Field[] fields, String delimiter, boolean includeType) {
        /**
         * create a string with all the fields of the entity like "field1 type1, field2
         * type2, ..."
         */
        StringJoiner joiner = new StringJoiner(delimiter, ", ", ")");
        for (Field field : fields) {
            if (!field.getName().equals("id") && !field.getName().equals("version")) {
                joiner.add(field.getName() + (includeType ? " " + getSqlType(field.getType()) : ""));
            }
        }
        return joiner.toString();
    }

    private void createTable(Connection connection, String tableName, Field[] fields) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(tableName)
                .append(" (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, version INT")
                .append(processFields(fields, ", ", true));

        PreparedStatement statement = connection.prepareStatement(sql.toString());
        statement.executeUpdate();
    }

    private PreparedStatement prepareInsertStatement(Connection connection, String tableName, Field[] fields)
            throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(tableName)
                .append(" (version")
                .append(processFields(fields, ", ", false))
                .append(" VALUES (?");

        for (int i = 2; i < fields.length; i++) {
            sql.append(", ?");
        }
        sql.append(")");

        return connection.prepareStatement(sql.toString(), PreparedStatement.RETURN_GENERATED_KEYS);
    }

    private void setFieldValues(PreparedStatement statement, Field[] fields, Object entity)
            throws SQLException, IllegalAccessException {     
        int paramIndex = 1;
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals("version")) {
                statement.setInt(paramIndex, 1);
                paramIndex++;
            } else if (!field.getName().equals("id")) {
                Object value = field.get(entity);
                statement.setObject(paramIndex, value);
                paramIndex++;
            }
        }
    }

private <T> void setIdField(T entity, ResultSet generatedKeys) throws SQLException {
    try {
        Field idField = getAccessibleField(entity.getClass(), "id");
        idField.set(entity, generatedKeys.getLong(1));
    } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new RuntimeException("Error setting id field on entity", e);
    }
}

    public void persist(Object entity) {
        try {
            // Get Class Name and Connection to the database
            String ClassName = entity.getClass().getSimpleName();
            Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // Create the table if it does not exist
            Field[] fields = entity.getClass().getDeclaredFields();
            createTable(connection, ClassName, fields);

            // Build the SQL query for inserting a record based on the entity's fields
            PreparedStatement statement = prepareInsertStatement(connection, ClassName, fields);

            // Set the values for the fields
            setFieldValues(statement, fields, entity);

            // Execute the query
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    setIdField(entity, generatedKeys);
                    }
                }
            }
        catch (SQLException | IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T merge(T entity) {
        try {
            String className = entity.getClass().getSimpleName();
            Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            Field[] fields = entity.getClass().getDeclaredFields();
            StringBuilder sql = new StringBuilder("UPDATE " + className + " SET ");

            for (Field field : fields) {
                if (!field.getName().equals("id")) {
                    if (field.getName().equals("version")) {
                        field.setAccessible(true);
                        int version = field.getInt(entity);
                        field.setInt(entity, version + 1); // Increment version
                    }
                    sql.append(field.getName()).append(" = ?, ");
                }
            }
            sql.delete(sql.length() - 2, sql.length()); // Remove the last comma
            sql.append(" WHERE id = ?");

            PreparedStatement statement = connection.prepareStatement(sql.toString());

            int i = 1;
            for (Field field : fields) {
                if (!field.getName().equals("id")) {
                    field.setAccessible(true);
                    statement.setObject(i, field.get(entity));
                    i++;
                }
            }
            Field idField = getAccessibleField(entity.getClass(), "id");
            statement.setObject(i, idField.get(entity));

            statement.executeUpdate();

            return entity;
        } catch (SQLException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T find(Class<T> entityClass, Object primaryKey) {
        try {
            String className = entityClass.getSimpleName();
            Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String sql = "SELECT * FROM " + className + " WHERE id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setObject(1, primaryKey);

            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                return null;
            }

            T entity = entityClass.getDeclaredConstructor().newInstance();
            ResultSetMetaData metaData = resultSet.getMetaData();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                String columnName = metaData.getColumnName(i);
                Object columnValue = resultSet.getObject(i);
                Field field = getAccessibleField(entityClass, columnName.toLowerCase());
                field.set(entity, columnValue);
            }

            return entity;
        } catch (SQLException | NoSuchFieldException | IllegalAccessException | InstantiationException
                | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(Object entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'remove'");
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'find'");
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'find'");
    }

    @Override
    public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode, Map<String, Object> properties) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'find'");
    }

    @Override
    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getReference'");
    }

    @Override
    public void flush() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'flush'");
    }

    @Override
    public void setFlushMode(FlushModeType flushMode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFlushMode'");
    }

    @Override
    public FlushModeType getFlushMode() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFlushMode'");
    }

    @Override
    public void lock(Object entity, LockModeType lockMode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lock'");
    }

    @Override
    public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lock'");
    }

    @Override
    public void refresh(Object entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'refresh'");
    }

    @Override
    public void refresh(Object entity, Map<String, Object> properties) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'refresh'");
    }

    @Override
    public void refresh(Object entity, LockModeType lockMode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'refresh'");
    }

    @Override
    public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'refresh'");
    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'clear'");
    }

    @Override
    public void detach(Object entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'detach'");
    }

    @Override
    public boolean contains(Object entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'contains'");
    }

    @Override
    public LockModeType getLockMode(Object entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLockMode'");
    }

    @Override
    public void setProperty(String propertyName, Object value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setProperty'");
    }

    @Override
    public Map<String, Object> getProperties() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getProperties'");
    }

    @Override
    public Query createQuery(String qlString) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createQuery'");
    }

    @Override
    public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createQuery'");
    }

    @Override
    public Query createQuery(CriteriaUpdate updateQuery) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createQuery'");
    }

    @Override
    public Query createQuery(CriteriaDelete deleteQuery) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createQuery'");
    }

    @Override
    public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createQuery'");
    }

    @Override
    public Query createNamedQuery(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNamedQuery'");
    }

    @Override
    public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNamedQuery'");
    }

    @Override
    public Query createNativeQuery(String sqlString) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNativeQuery'");
    }

    @Override
    public Query createNativeQuery(String sqlString, Class resultClass) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNativeQuery'");
    }

    @Override
    public Query createNativeQuery(String sqlString, String resultSetMapping) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNativeQuery'");
    }

    @Override
    public StoredProcedureQuery createNamedStoredProcedureQuery(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNamedStoredProcedureQuery'");
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createStoredProcedureQuery'");
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createStoredProcedureQuery'");
    }

    @Override
    public StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createStoredProcedureQuery'");
    }

    @Override
    public void joinTransaction() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'joinTransaction'");
    }

    @Override
    public boolean isJoinedToTransaction() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isJoinedToTransaction'");
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'unwrap'");
    }

    @Override
    public Object getDelegate() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDelegate'");
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

    @Override
    public boolean isOpen() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isOpen'");
    }

    @Override
    public EntityTransaction getTransaction() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransaction'");
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityManagerFactory'");
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCriteriaBuilder'");
    }

    @Override
    public Metamodel getMetamodel() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMetamodel'");
    }

    @Override
    public <T> EntityGraph<T> createEntityGraph(Class<T> rootType) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createEntityGraph'");
    }

    @Override
    public EntityGraph<?> createEntityGraph(String graphName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createEntityGraph'");
    }

    @Override
    public EntityGraph<?> getEntityGraph(String graphName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityGraph'");
    }

    @Override
    public <T> List<EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityGraphs'");
    }

}