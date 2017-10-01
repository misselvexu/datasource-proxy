package net.ttddyy.dsproxy.proxy;

import net.ttddyy.dsproxy.listener.CallCheckMethodExecutionListener;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.jdk.ConnectionInvocationHandler;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tadaya Tsuyukubo
 */
public class DataSourceProxyLogicMockTest {

    private static final String DS_NAME = "myDS";

    @Test
    public void testGetConnection() throws Throwable {
        DataSource ds = mock(DataSource.class);
        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = DataSource.class.getMethod("getConnection");
        Object result = logic.invoke(method, null);

        assertThat(result, is(instanceOf(Connection.class)));
        verifyConnection((Connection) result);
        verify(ds).getConnection();
    }

    private DataSourceProxyLogic getProxyLogic(DataSource ds) {
        QueryExecutionListener listener = mock(QueryExecutionListener.class);

        ProxyConfig proxyConfig = ProxyConfig.Builder.create()
                .dataSourceName(DS_NAME)
                .queryListener(listener)
                .build();
        return new DataSourceProxyLogic(ds, proxyConfig);
    }

    private void verifyConnection(Connection conn) {
        assertThat(conn, notNullValue());

        assertThat(Proxy.isProxyClass(conn.getClass()), is(true));
        InvocationHandler handler = Proxy.getInvocationHandler(conn);
        assertThat(handler, is(instanceOf(ConnectionInvocationHandler.class)));

        assertThat(conn, is(instanceOf(ProxyJdbcObject.class)));
    }

    @Test
    public void testGetTarget() throws Throwable {
        DataSource ds = mock(DataSource.class);
        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = ProxyJdbcObject.class.getMethod("getTarget");
        Object result = logic.invoke(method, null);

        assertThat(result, is(instanceOf(DataSource.class)));
        DataSource resultDS = (DataSource) result;
        assertThat(resultDS, is(sameInstance(ds)));
    }

    @Test
    public void testUnwrap() throws Throwable {
        DataSource ds = mock(DataSource.class);
        when(ds.unwrap(String.class)).thenReturn("called");

        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = Connection.class.getMethod("unwrap", Class.class);
        Object result = logic.invoke(method, new Object[]{String.class});

        verify(ds).unwrap(String.class);
        assertThat(result, is(instanceOf(String.class)));
        assertThat((String) result, is("called"));
    }

    @Test
    public void testIsWrapperFor() throws Throwable {
        DataSource ds = mock(DataSource.class);
        when(ds.isWrapperFor(String.class)).thenReturn(true);

        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = Connection.class.getMethod("isWrapperFor", Class.class);
        Object result = logic.invoke(method, new Object[]{String.class});

        verify(ds).isWrapperFor(String.class);
        assertThat(result, is(instanceOf(boolean.class)));
        assertThat((Boolean) result, is(true));
    }

    @Test
    public void testToString() throws Throwable {
        DataSource ds = mock(DataSource.class);

        when(ds.toString()).thenReturn("my ds");
        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = Object.class.getMethod("toString");
        Object result = logic.invoke(method, null);

        assertThat(result, is(instanceOf(String.class)));
        assertThat((String) result, is(ds.getClass().getSimpleName() + " [my ds]"));
    }

    @Test
    public void testHashCode() throws Throwable {
        DataSource ds = mock(DataSource.class);
        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = Object.class.getMethod("hashCode");
        Object result = logic.invoke(method, null);

        assertThat(result, is(instanceOf(Integer.class)));
        assertThat((Integer) result, is(ds.hashCode()));
    }

    @Test
    public void testEquals() throws Throwable {
        DataSource ds = mock(DataSource.class);
        DataSourceProxyLogic logic = getProxyLogic(ds);

        Method method = Object.class.getMethod("equals", Object.class);

        // equals(null)
        Object result = logic.invoke(method, new Object[]{null});
        assertThat(result, is(instanceOf(Boolean.class)));
        assertThat((Boolean) result, is(false));

        // equals(true)
        result = logic.invoke(method, new Object[]{ds});
        assertThat(result, is(instanceOf(Boolean.class)));
        assertThat((Boolean) result, is(true));
    }

    @Test
    public void methodExecutionListener() throws Throwable {
        CallCheckMethodExecutionListener listener = new CallCheckMethodExecutionListener();

        DataSource ds = mock(DataSource.class);

        ProxyConfig proxyConfig = ProxyConfig.Builder.create()
                .dataSourceName(DS_NAME)
                .methodListener(listener)
                .build();
        DataSourceProxyLogic logic = new DataSourceProxyLogic(ds, proxyConfig);


        Method method = DataSource.class.getMethod("getConnection");
        logic.invoke(method, new Object[]{});

        assertTrue(listener.isBeforeMethodCalled());
        assertTrue(listener.isAfterMethodCalled());

        MethodExecutionContext executionContext = listener.getAfterMethodContext();
        assertSame("method should come from interface",
                DataSource.class, executionContext.getMethod().getDeclaringClass());
        assertSame("getConnection", executionContext.getMethod().getName());
        assertSame(ds, executionContext.getTarget());
        assertNull(executionContext.getConnectionInfo());
    }

}