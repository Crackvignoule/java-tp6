// Author : Killian PAVY
package tp6;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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

// TODO Change all references to Club and its fields so that they are generic and can be used for any entity
// TODO Clean the code, ex: create functions to simplify code especially for the reflection parts
// TODO Refactor the code to avoid code duplication (especially for the SQL queries)
// TODO Generic function for find
// TODO Add comments
// NOTE: I tried to use abstract to avoid implementing all the methods but it did not work because we need to instanciate the class in the tests

public class EntityManagerImpl implements EntityManager {

private static final String DB_URL = "jdbc:hsqldb:mem:mymemdb";
private static final String DB_USER = "SA";
private static final String DB_PASSWORD = "";

private Field getAccessibleField(Class<?> entityClass, String fieldName) throws NoSuchFieldException {
    Field field = entityClass.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field;
}

private String getSqlType(Class<?> type) {
    if (type == int.class || type == Integer.class) {
        return "INT";
    } else if (type == long.class || type == Long.class) {
        return "BIGINT";
    } else if (type == double.class || type == Double.class) {
        return "DOUBLE";
    } else if (type == String.class) {
        return "VARCHAR(255)";
    } else {
        // You can add more types as needed
        // For types that can't be mapped to SQL, throw an exception or return a default type
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}

public void persist(Object entity) {
    try {
        // Create the table if it does not exist
        String class_name = entity.getClass().getSimpleName();
        Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        
        // Build the SQL query for creating the table based on the entity's fields
        Field[] fields = entity.getClass().getDeclaredFields();
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
            .append(class_name)
            .append(" (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, version INT");

        for (Field field : fields) {
            if (!field.getName().equals("id") && !field.getName().equals("version")) {
                sql.append(", ").append(field.getName()).append(" ").append(getSqlType(field.getType()));
            }
        }

        sql.append(")");

        PreparedStatement statement = connection.prepareStatement(sql.toString());
        statement.executeUpdate();

        // Build the SQL query for inserting a record based on the entity's fields
        sql = new StringBuilder("INSERT INTO ")
        .append(class_name)
        .append(" (version");
            
        for (Field field : fields) {
        if (!field.getName().equals("id") && !field.getName().equals("version")) {
            sql.append(", ").append(field.getName());
        }
        }
        
        sql.append(") VALUES (?");
        
        for (int i = 2; i < fields.length; i++) {
        sql.append(", ?");
        }
        
        sql.append(")");
        
        // Prepare the statement
        statement = connection.prepareStatement(sql.toString(), PreparedStatement.RETURN_GENERATED_KEYS);

        // Set the values for the fields
        int paramIndex = 1;  // Start at 1 for the PreparedStatement parameters
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.getName().equals("version")) {
                statement.setInt(paramIndex, 1);  // Set version to 1
                paramIndex++;
            } else if (!field.getName().equals("id")) {
                statement.setObject(paramIndex, field.get(entity));
                paramIndex++;
            }
        }

        statement.executeUpdate();

        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                try {
                    Field idField = getAccessibleField(entity.getClass(), "id");
                    idField.set(entity, generatedKeys.getLong(1));
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            else {
                throw new SQLException("Creating user failed, no ID obtained.");
            }
        }

    } catch (SQLException | IllegalArgumentException | IllegalAccessException e) {
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
                    field.setInt(entity, version + 1);  // Increment version
                }
                sql.append(field.getName()).append(" = ?, ");
            }
        }
        sql.delete(sql.length() - 2, sql.length());  // Remove the last comma
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
    } catch (SQLException | NoSuchFieldException | IllegalAccessException | InstantiationException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
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