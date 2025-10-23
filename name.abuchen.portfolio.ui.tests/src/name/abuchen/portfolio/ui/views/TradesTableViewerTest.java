package name.abuchen.portfolio.ui.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.trades.Trade;
import name.abuchen.portfolio.snapshot.trades.TradeCollector;
import name.abuchen.portfolio.ui.views.trades.TradeElement;
import name.abuchen.portfolio.ui.util.viewers.Column;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.TouchClientListener;
import name.abuchen.portfolio.ui.views.columns.NameColumn;

@SuppressWarnings("nls")
public class TradesTableViewerTest
{
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
    public void renamingSecurityTouchesClient() throws Exception
    {
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
    }
}
