package name.abuchen.portfolio.ui.views;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.jface.preference.PreferenceStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.jface.databinding.swt.DisplayRealm;

import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.junit.TaxonomyBuilder;
import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.trades.Trade;
import name.abuchen.portfolio.snapshot.trades.TradeCollector;
import name.abuchen.portfolio.snapshot.trades.TradeCategory;
import name.abuchen.portfolio.snapshot.trades.TradesGroupedByTaxonomy;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.views.trades.TradeElement;
import name.abuchen.portfolio.ui.util.viewers.Column;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.TouchClientListener;
import name.abuchen.portfolio.ui.util.viewers.MoneyColorLabelProvider;
import name.abuchen.portfolio.ui.views.columns.NameColumn;
import name.abuchen.portfolio.ui.util.viewers.ShowHideColumnHelper;

import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

@SuppressWarnings("nls")
public class TradesTableViewerTest
{
    private static PortfolioPlugin previousPlugin;
    private static PortfolioPlugin testPlugin;
    private static Display display;
    private static Realm realm;
    private static boolean weCreatedDisplay;

    @BeforeClass
    public static void ensurePreferenceStore() throws Exception
    {
        previousPlugin = PortfolioPlugin.getDefault();
        if (previousPlugin == null)
        {
            testPlugin = new PortfolioPlugin();
            ensurePreferenceStore(testPlugin);
        }
        else
        {
            ensurePreferenceStore(previousPlugin);
        }

        setupDisplayRealm();

    }

    @AfterClass
    public static void resetPortfolioPlugin() throws Exception
    {
        synchronized (TradesTableViewerTest.class)
        {
            if (testPlugin != null)
            {
                var instanceField = PortfolioPlugin.class.getDeclaredField("instance"); //$NON-NLS-1$
                instanceField.setAccessible(true);
                instanceField.set(null, previousPlugin);
                testPlugin = null;
            }
        }

        if (display != null && !display.isDisposed())
        {
            display.syncExec(() -> {
                if (weCreatedDisplay && !display.isDisposed())
                {
                    display.dispose();
                }
            });
        }

        display = null;
        realm = null;
        weCreatedDisplay = false;
    }

    private static void ensurePreferenceStore(PortfolioPlugin plugin) throws Exception
    {
        var preferenceStoreField = PortfolioPlugin.class.getDeclaredField("preferenceStore"); //$NON-NLS-1$
        preferenceStoreField.setAccessible(true);
        if (preferenceStoreField.get(plugin) == null)
            preferenceStoreField.set(plugin, new PreferenceStore());
    }

    private static synchronized void setupDisplayRealm()
    {
        if (display != null)
            return;

        try
        {
            // 1. TRY to create a new Display. This thread will become the UI thread.
            display = new Display();
            realm = DisplayRealm.getRealm(display);
            weCreatedDisplay = true;
        }
        catch (SWTException e)
        {
            // 2. If it fails, one *already exists*. Get it and attach to it.
            // We CANNOT call any methods on it from this thread.
            display = Display.getDefault();
            realm = DisplayRealm.getRealm(display);
            weCreatedDisplay = false; // We are re-using it
        }
    }

    private void runWithDisplayRealm(ThrowingRunnable runnable) throws Exception
    {
        Exception[] exception = new Exception[1];

        display.syncExec(() -> {
            try
            {
                Realm.runWithDefault(realm, () -> {
                    try
                    {
                        runnable.run();
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                });
            }
            catch (Exception e)
            {
                exception[0] = e;
            }
        });

        if (exception[0] != null)
        {
            if (exception[0] instanceof RuntimeException runtimeException
                            && runtimeException.getCause() instanceof Exception nested)
            {
                throw nested;
            }
            throw exception[0];
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    @Test
    public void tradeReturnIsNotWeightedWhenGroupedByTaxonomy() throws Exception
    {
        Client client = new Client();

        Security security = new SecurityBuilder() //
                        .addPrice("2020-01-01", Values.Quote.factorize(100)) //
                        .addPrice("2020-02-01", Values.Quote.factorize(110)) //
                        .addTo(client);

        Account account = new AccountBuilder() //
                        .deposit_("2020-01-01", Values.Amount.factorize(20000)) //
                        .addTo(client);

        new PortfolioBuilder(account) //
                        .buy(security, "2020-01-01", Values.Share.factorize(100), Values.Amount.factorize(10000)) //
                        .sell(security, "2020-02-01", Values.Share.factorize(100), Values.Amount.factorize(11000)) //
                        .addTo(client);

        TradeCollector collector = new TradeCollector(client, new TestCurrencyConverter());
        List<Trade> trades = collector.collect(security);

        Trade trade = trades.get(0);
        TradeElement element = new TradeElement(trade, 0, 0.5);

        double expected = trade.getReturn();

        assertThat(expected, closeTo(0.1, 0.0000001));
        assertThat(TradesTableViewer.getReturnValue(element), closeTo(expected, 0.0000001));
        assertThat(TradesTableViewer.getReturnValue(trade), closeTo(expected, 0.0000001));
    }

    @Test
    public void weightedSharesFollowSecuritySplitRounding() throws Exception
    {
        Client client = new Client();

        Security security = new SecurityBuilder() //
                        .addPrice("2020-01-01", Values.Quote.factorize(100)) //
                        .addPrice("2020-02-01", Values.Quote.factorize(110)) //
                        .addTo(client);

        Account account = new AccountBuilder() //
                        .deposit_("2020-01-01", Values.Amount.factorize(20000)) //
                        .addTo(client);

        double tradedShares = 10.0 / 3.0;

        new PortfolioBuilder(account) //
                        .buy(security, "2020-01-01", Values.Share.factorize(tradedShares),
                                        Values.Amount.factorize(tradedShares * 100)) //
                        .sell(security, "2020-02-01", Values.Share.factorize(tradedShares),
                                        Values.Amount.factorize(tradedShares * 110)) //
                        .addTo(client);

        TradeCollector collector = new TradeCollector(client, new TestCurrencyConverter());
        List<Trade> trades = collector.collect(security);
        Trade trade = trades.get(0);

        int partialWeight = Classification.ONE_HUNDRED_PERCENT / 3;
        double taxonomyWeight = partialWeight / (double) Classification.ONE_HUNDRED_PERCENT;

        TradeElement element = new TradeElement(trade, 0, taxonomyWeight);

        long expectedWeightedShares = BigDecimal.valueOf(trade.getShares()) //
                        .multiply(BigDecimal.valueOf(partialWeight), Values.MC) //
                        .divide(Classification.ONE_HUNDRED_PERCENT_BD, Values.MC) //
                        .setScale(0, RoundingMode.HALF_DOWN).longValue();

        assertThat(element.getWeightedShares(), is(expectedWeightedShares));
        assertThat(new TradeElement(trade, 0, 1.0).getWeightedShares(), is(trade.getShares()));
    }

    @Test
    public void categoryAndTradeRowsShareCurrencyFormattingInSecurityCurrencyMode() throws Exception
    {
        runWithDisplayRealm(() -> {
            Client client = new Client();
            client.setBaseCurrency(CurrencyUnit.EUR);

            Security usdSecurity = new SecurityBuilder(CurrencyUnit.USD) //
                            .addPrice("2015-01-02", Values.Quote.factorize(100)) //
                            .addPrice("2015-01-09", Values.Quote.factorize(110)) //
                            .addTo(client);

            Account account = new AccountBuilder() //
                            .deposit_("2015-01-01", Values.Amount.factorize(200000)) //
                            .addTo(client);

            new PortfolioBuilder(account) //
                            .buy(usdSecurity, "2015-01-02", Values.Share.factorize(100), Values.Amount.factorize(10000)) //
                            .sell(usdSecurity, "2015-01-09", Values.Share.factorize(100), Values.Amount.factorize(11000)) //
                            .addTo(client);

            Taxonomy taxonomy = new TaxonomyBuilder() //
                            .addClassification("equities") //
                            .addTo(client);

            Classification equities = taxonomy.getClassificationById("equities");
            equities.addAssignment(new Classification.Assignment(usdSecurity));

            TestCurrencyConverter converter = new TestCurrencyConverter(CurrencyUnit.EUR);
            TradeCollector collector = new TradeCollector(client, converter.with(CurrencyUnit.USD));

            List<Trade> trades = collector.collect(usdSecurity);
            Trade trade = trades.get(0);

            TradesGroupedByTaxonomy grouped = new TradesGroupedByTaxonomy(taxonomy, trades, converter);
            TradeCategory category = grouped.asList().stream()
                            .filter(c -> c.getTaxonomyClassification() == equities).findFirst().orElse(null);

            TradeElement tradeElement = new TradeElement(trade, 1, 1.0);
            TradeElement categoryElement = new TradeElement(category, 0);

            MoneyColorLabelProvider provider = new MoneyColorLabelProvider(element -> {
                if (element instanceof TradeElement te)
                {
                    if (te.isTrade())
                        return te.getTrade().getProfitLoss();
                    if (te.isCategory())
                        return te.getCategory().getTotalProfitLoss();
                }
                return null;
            }, client);

            String tradeText = provider.getText(tradeElement);
            String categoryText = provider.getText(categoryElement);

            assertThat(category, notNullValue());
            assertThat(tradeText, notNullValue());
            assertThat(categoryText, notNullValue());
            assertThat(tradeText, is(categoryText));
            assertThat(tradeText, containsString(CurrencyUnit.USD));
        });
    }

    @Test
    public void renamingSecurityTouchesClient() throws Exception
    {
        runWithDisplayRealm(() -> {
            Client client = new Client();

            Security security = new SecurityBuilder() //
                            .addPrice("2020-01-01", Values.Quote.factorize(100)) //
                            .addPrice("2020-02-01", Values.Quote.factorize(110)) //
                            .addTo(client);
            security.setName("Original");

            Account account = new AccountBuilder() //
                            .deposit_("2020-01-01", Values.Amount.factorize(20000)) //
                            .addTo(client);

            new PortfolioBuilder(account) //
                            .buy(security, "2020-01-01", Values.Share.factorize(100), Values.Amount.factorize(10000)) //
                            .sell(security, "2020-02-01", Values.Share.factorize(100), Values.Amount.factorize(11000)) //
                            .addTo(client);

            TradeCollector collector = new TradeCollector(client, new TestCurrencyConverter());
            Trade trade = collector.collect(security).get(0);

            AtomicBoolean touched = new AtomicBoolean(false);
            client.addPropertyChangeListener("touch", event -> touched.set(true));

            Column column = new NameColumn(client);
            column.getEditingSupport().addListener(new TouchClientListener(client));

            column.getEditingSupport().setValue(trade, "Renamed Security");

            assertThat(security.getName(), is("Renamed Security"));
            assertThat(client.getSecurities().get(0).getName(), is("Renamed Security"));
            assertThat(touched.get(), is(true));
        });
    }

    @Test
    public void renamingSecurityRefreshesAllTradeElements() throws Exception
    {
        runWithDisplayRealm(() -> {
            Client client = new Client();

            Security security = new SecurityBuilder() //
                            .addPrice("2020-01-01", Values.Quote.factorize(100)) //
                            .addPrice("2020-02-01", Values.Quote.factorize(110)) //
                            .addTo(client);
            security.setName("Original");

            Account account = new AccountBuilder() //
                            .deposit_("2020-01-01", Values.Amount.factorize(20000)) //
                            .addTo(client);

            new PortfolioBuilder(account) //
                            .buy(security, "2020-01-01", Values.Share.factorize(100), Values.Amount.factorize(10000)) //
                            .sell(security, "2020-02-01", Values.Share.factorize(100), Values.Amount.factorize(11000)) //
                            .addTo(client);

            TradeCollector collector = new TradeCollector(client, new TestCurrencyConverter());
            Trade trade = collector.collect(security).get(0);

            TradesTableViewer viewer = new TradesTableViewer(new DummyFinanceView(client));

            TableViewer tableViewer = mock(TableViewer.class);
            Field tradesField = TradesTableViewer.class.getDeclaredField("trades");
            tradesField.setAccessible(true);
            tradesField.set(viewer, tableViewer);

            ShowHideColumnHelper helper = mock(ShowHideColumnHelper.class);
            List<Column> columns = new ArrayList<>();
            doAnswer(invocation -> {
                Column column = invocation.getArgument(0);
                columns.add(column);
                return null;
            }).when(helper).addColumn(any(Column.class));
            doAnswer(invocation -> columns.stream()).when(helper).getColumns();

            Method method = TradesTableViewer.class.getDeclaredMethod("createTradesColumns", ShowHideColumnHelper.class,
                            TradesTableViewer.ViewMode.class);
            method.setAccessible(true);
            method.invoke(viewer, helper, TradesTableViewer.ViewMode.MULTIPLE_SECURITIES);

            Column nameColumn = columns.stream().filter(NameColumn.class::isInstance).findFirst().orElseThrow();
            ColumnLabelProvider provider = (ColumnLabelProvider) nameColumn.getLabelProvider().get();

            List<TradeElement> elements = List.of(new TradeElement(trade, 0, 1.0), new TradeElement(trade, 1, 0.5));
            List<String> renderedNames = elements.stream().map(provider::getText)
                            .collect(Collectors.toCollection(ArrayList::new));

            assertThat(renderedNames, everyItem(is("Original")));

            doAnswer(invocation -> {
                for (int i = 0; i < elements.size(); i++)
                    renderedNames.set(i, provider.getText(elements.get(i)));
                return null;
            }).when(tableViewer).refresh(true);

            nameColumn.getEditingSupport().setValue(elements.get(0), "Renamed Security");

            assertThat(renderedNames, everyItem(is("Renamed Security")));
            verify(tableViewer).refresh(true);
        });
    }

    private static final class DummyFinanceView extends AbstractFinanceView
    {
        private final Client client;

        private DummyFinanceView(Client client)
        {
            this.client = client;
        }

        @Override
        protected String getDefaultTitle()
        {
            return "Test";
        }

        @Override
        protected Control createBody(Composite parent)
        {
            return null;
        }

        @Override
        public Client getClient()
        {
            return client;
        }
    }
}
