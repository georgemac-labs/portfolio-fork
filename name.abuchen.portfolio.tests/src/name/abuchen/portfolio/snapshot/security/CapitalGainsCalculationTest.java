package name.abuchen.portfolio.snapshot.security;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

import name.abuchen.portfolio.junit.AccountBuilder;
import name.abuchen.portfolio.junit.PortfolioBuilder;
import name.abuchen.portfolio.junit.SecurityBuilder;
import name.abuchen.portfolio.junit.TestCurrencyConverter;
import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.PortfolioTransferEntry;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.util.Interval;

@SuppressWarnings("nls")
public class CapitalGainsCalculationTest
{

    @Test
    public void testPartialTransfersAndTrailMatches()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addTo(client);

        Portfolio portfolioA = new PortfolioBuilder(new Account("one"))
                        .inbound_delivery(security, "2021-01-01", Values.Share.factorize(10),
                                        Values.Amount.factorize(1000))
                        .outbound_delivery(security, "2021-01-02", Values.Share.factorize(5),
                                        Values.Amount.factorize(500), 0, 0)
                        .addTo(client);

        Portfolio portfolioB = new PortfolioBuilder(new Account("two")).addTo(client);

        PortfolioTransferEntry transfer = new PortfolioTransferEntry(portfolioA, portfolioB);
        transfer.setSecurity(security);
        transfer.setDate(LocalDateTime.parse("2021-01-03T00:00"));
        transfer.setShares(Values.Share.factorize(5));
        transfer.setAmount(Values.Amount.factorize(600));
        transfer.setCurrencyCode(security.getCurrencyCode());

        transfer.insert();

        var interval = Interval.of(LocalDate.parse("2020-12-31"), LocalDate.parse("2021-01-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);

        new SecurityPerformanceSnapshotComparator(snapshot,
                        LazySecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(), interval))
                                        .compare();

        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        Money eur100 = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(100));
        assertThat(record.getCapitalGainsOnHoldings(), is(eur100));

        CapitalGainsRecord unrealizedCapitalGains = record.getUnrealizedCapitalGains();
        assertThat(unrealizedCapitalGains.getCapitalGains(), is(eur100));
        assertThat(unrealizedCapitalGains.getCapitalGainsTrail().getValue(), is(eur100));

    }

    @Test
    public void testFifoBuySellTransactions()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addPrice("2013-03-01", Values.Quote.factorize(100)) //
                        .addTo(client);

        new PortfolioBuilder() //
                        .buy(security, "2010-01-01", Values.Share.factorize(109), Values.Amount.factorize(3149.20)) //
                        .sell(security, "2010-02-01", Values.Share.factorize(15), Values.Amount.factorize(531.50)) //
                        .buy(security, "2010-03-01", Values.Share.factorize(52), Values.Amount.factorize(1684.92)) //
                        .buy(security, "2010-03-01", Values.Share.factorize(32), Values.Amount.factorize(959.30)) //
                        .addTo(client);

        var interval = Interval.of(LocalDate.parse("2009-12-31"), LocalDate.parse("2021-01-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);
        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        // expected Realized Gains for FIFO :
        // 531.5 - 3149.20 * 15/109 = 98,1238532110092
        Money expectedGains = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(98.12));
        CapitalGainsRecord realizedCapitalGains = record.getRealizedCapitalGains();
        assertThat(realizedCapitalGains.getCapitalGains(), is(expectedGains));

        // expected Realized Gains for moving average is identical because it is
        // only one buy
        CapitalGainsRecord realizedCapitalGainsMovingAvg = record.getRealizedCapitalGainsMovingAvg();
        assertThat(realizedCapitalGainsMovingAvg.getCapitalGains(), is(expectedGains));

        // expected Unrealized Gains for FIFO :
        // 100 * 178 - [3149.2 * (109-15) / 109 + 1684.92 + 959.3] =
        // 12439,956146789
        Money expectedUnrealizedGains = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(12439.96));
        CapitalGainsRecord unRealizedCapitalGains = record.getUnrealizedCapitalGains();
        assertThat(unRealizedCapitalGains.getCapitalGains(), is(expectedUnrealizedGains));

        // expected Unrealized Gains for moving average is identical because it
        // is only one buy
        CapitalGainsRecord unRealizedCapitalGainsMovingAvg = record.getUnrealizedCapitalGainsMovingAvg();
        assertThat(unRealizedCapitalGainsMovingAvg.getCapitalGains(), is(expectedUnrealizedGains));

    }

    @Test
    public void testFifoBuySellTransactions2()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addPrice("2013-03-01", Values.Quote.factorize(100)) //
                        .addTo(client);
        new PortfolioBuilder() //
                        .buy(security, "2010-01-01", Values.Share.factorize(109), Values.Amount.factorize(3149.20)) //
                        .buy(security, "2010-02-01", Values.Share.factorize(52), Values.Amount.factorize(1684.92)) //
                        .sell(security, "2010-03-01", Values.Share.factorize(15), Values.Amount.factorize(531.50)) //
                        .addTo(client);

        var interval = Interval.of(LocalDate.parse("2009-12-31"), LocalDate.parse("2021-01-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);
        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        // expected Realized Gains for FIFO :
        // 531.5 - 3149.20 * 15/109 = 98,1238532110092
        Money expectedGains = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(98.12));
        CapitalGainsRecord realizedCapitalGains = record.getRealizedCapitalGains();
        assertThat(realizedCapitalGains.getCapitalGains(), is(expectedGains));

        // expected Realized Gains for Moving average
        // 531.5 - (3149.20 + 1684.92) * 15/(109 + 52) = 81,116149068323
        Money expectedGainsMovingAvg = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(81.12));
        CapitalGainsRecord realizedCapitalGainsMovingAvg = record.getRealizedCapitalGainsMovingAvg();
        assertThat(realizedCapitalGainsMovingAvg.getCapitalGains(), is(expectedGainsMovingAvg));

        // expected Unrealized Gains for FIFO :
        // 146 * 100 - [3149,20 + 1684,92 - (3149,20 * 15/109)] =
        // 10199,256146789
        Money expectedUnrealizedGains = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(10199.26));
        CapitalGainsRecord unRealizedCapitalGainsFiFO = record.getUnrealizedCapitalGains();
        assertThat(unRealizedCapitalGainsFiFO.getCapitalGains(), is(expectedUnrealizedGains));

        // expected Unrealized Gains for Moving average
        // 146 * 100 - (3149.20 + 1684.92) * 146 / (109 + 52) = 10216,2638509317
        Money expectedUnrealizedGainsMovingAvg = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(10216.26));
        CapitalGainsRecord unRealizedCapitalGainsMovingAvg = record.getUnrealizedCapitalGainsMovingAvg();
        assertThat(unRealizedCapitalGainsMovingAvg.getCapitalGains(), is(expectedUnrealizedGainsMovingAvg));
    }

    /**
     * Test case for the example discussed in
     * https://github.com/portfolio-performance/portfolio/pull/4546
     */
    @Test
    public void testFifoBuySellTransactions3()
    {
        var client = new Client();
        client.setBaseCurrency(CurrencyUnit.EUR);

        var security = new SecurityBuilder(CurrencyUnit.USD) //
                        .addPrice("2025-01-01", Values.Quote.factorize(80)) //
                        .addTo(client);

        var account = new AccountBuilder(CurrencyUnit.EUR) //
                        .addTo(client);

        new PortfolioBuilder(account) //
                        .inbound_delivery(security, "2024-01-01", Values.Share.factorize(100),
                                        new Transaction.Unit(Transaction.Unit.Type.GROSS_VALUE,
                                                        Money.of(CurrencyUnit.EUR, Values.Amount.factorize(4500)),
                                                        Money.of(CurrencyUnit.USD, Values.Amount.factorize(5000)),
                                                        BigDecimal.valueOf(0.90)) //
                        )
                        .inbound_delivery(security, "2024-02-01", Values.Share.factorize(50),
                                        new Transaction.Unit(Transaction.Unit.Type.GROSS_VALUE,
                                                        Money.of(CurrencyUnit.EUR, Values.Amount.factorize(2550)),
                                                        Money.of(CurrencyUnit.USD, Values.Amount.factorize(3000)),
                                                        BigDecimal.valueOf(0.85)) //
                        )
                        .outbound_delivery(security, "2024-03-01", Values.Share.factorize(50),
                                        new Transaction.Unit(Transaction.Unit.Type.GROSS_VALUE,
                                                        Money.of(CurrencyUnit.EUR, Values.Amount.factorize(3080)),
                                                        Money.of(CurrencyUnit.USD, Values.Amount.factorize(3500)),
                                                        BigDecimal.valueOf(0.88)) //
                        ).addTo(client);

        var interval = Interval.of(LocalDate.parse("2023-12-31"), LocalDate.parse("2024-12-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);
        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        // FIFO
        var usingFIFO = record.getRealizedCapitalGains();

        // expected realized gains for FIFO
        // [revenue in EUR] - [partial cost of first buy]
        // 3080 - (50/100) * 4500 = 830
        assertThat(usingFIFO.getCapitalGains(), //
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(830))));

        // moving average
        var usingMovingAverage = record.getRealizedCapitalGainsMovingAvg();

        // expected realized gains
        // [revenue in EUR] - [average costs of first and second buy]
        // 3080 - (50/150) * (4500 + 2550) = 730
        assertThat(usingMovingAverage.getCapitalGains(), //
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(730))));

        // expected forex gains
        // [average costs in USD] * [sale transaction exchange rate] - [average
        // costs in EUR]
        // (50/150) * (5000+3000) * 0.88 - (50/150) * (4500+2550) = -3.33
        assertThat(usingMovingAverage.getForexCaptialGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(-3.33))));

        // moving average for unrealized gains
        var realizedUsingMovingAverage = record.getUnrealizedCapitalGainsMovingAvg();

        // expected realized gains
        // [current valuation in EUR] - [average costs of first and second buy]
        // exchange rate from the test currency converter is EUR/USD 1.1588
        // (100 * 80 / 1.1588) - (100/150) * (4500+2550) = 2203.69
        assertThat(realizedUsingMovingAverage.getCapitalGains(), //
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(2203.69))));

        // expected forex gains
        // [average costs in USD] * [sale transaction exchange rate] - [average
        // costs in EUR]
        // (100/150) * (5000+3000) / 1.1588 - (100/150) * (4500+2550) = -97.5376
        assertThat(realizedUsingMovingAverage.getForexCaptialGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(-97.54))));

    }

    @Test
    public void testShortSaleRealizedAndUnrealizedGains()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addPrice("2024-12-31", Values.Quote.factorize(60)).addTo(client);

        new PortfolioBuilder() //
                        .sell(security, "2024-01-02", Values.Share.factorize(10), Values.Amount.factorize(1000)) //
                        .buy(security, "2024-01-10", Values.Share.factorize(4), Values.Amount.factorize(320)) //
                        .buy(security, "2024-01-15", Values.Share.factorize(3), Values.Amount.factorize(210)) //
                        .addTo(client);

        var interval = Interval.of(LocalDate.parse("2023-12-31"), LocalDate.parse("2024-12-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);
        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        assertThat(record.getRealizedCapitalGains().getCapitalGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(170))));
        assertThat(record.getRealizedCapitalGains().getForexCaptialGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(0))));

        CapitalGainsRecord unrealized = record.getUnrealizedCapitalGains();
        assertThat(unrealized.getCapitalGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(120))));
        assertThat(unrealized.getForexCaptialGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(0))));
    }

    @Test
    public void testShortCoverResidualTrailIsFractioned()
    {
        Client client = new Client();

        Security security = new SecurityBuilder().addTo(client);

        new PortfolioBuilder() //
                        .sell(security, "2024-02-01", Values.Share.factorize(5), Values.Amount.factorize(500)) //
                        .buy(security, "2024-02-05", Values.Share.factorize(8), Values.Amount.factorize(640)) //
                        .sell(security, "2024-02-10", Values.Share.factorize(3), Values.Amount.factorize(270)) //
                        .addTo(client);

        var interval = Interval.of(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, new TestCurrencyConverter(),
                        interval);
        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        Money expected = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(130));
        CapitalGainsRecord realized = record.getRealizedCapitalGains();
        assertThat(realized.getCapitalGains(), is(expected));
        assertThat(realized.getCapitalGainsTrail().getValue(), is(expected));
        assertThat(realized.getForexCaptialGains(), is(Money.of(CurrencyUnit.EUR, 0)));
        assertThat(realized.getForexCapitalGainsTrail().isEmpty(), is(true));
    }

    @Test
    public void testShortSaleWithForexRealizedAndUnrealizedGains()
    {
        Client client = new Client();
        client.setBaseCurrency(CurrencyUnit.EUR);

        Security security = new SecurityBuilder(CurrencyUnit.USD) //
                        .addPrice("2015-01-16", Values.Quote.factorize(90)) //
                        .addTo(client);

        Account account = new AccountBuilder(CurrencyUnit.EUR) //
                        .deposit_("2015-01-01", Values.Amount.factorize(2000)) //
                        .addTo(client);

        Portfolio portfolio = new PortfolioBuilder(account).addTo(client);

        CurrencyConverter converter = new TestCurrencyConverter();

        BuySellEntry shortSale = new BuySellEntry(portfolio, account);
        shortSale.setType(PortfolioTransaction.Type.SELL);
        shortSale.setSecurity(security);
        shortSale.setDate(LocalDateTime.parse("2015-01-06T00:00"));
        shortSale.setShares(Values.Share.factorize(8));
        Money saleAmount = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(738.63));
        shortSale.setMonetaryAmount(saleAmount);
        Money saleForex = Money.of(CurrencyUnit.USD, Values.Amount.factorize(880));
        BigDecimal saleRate = BigDecimal.valueOf(saleAmount.getAmount())
                        .divide(BigDecimal.valueOf(saleForex.getAmount()), Values.MC)
                        .setScale(10, RoundingMode.HALF_UP);
        shortSale.getPortfolioTransaction()
                        .addUnit(new Transaction.Unit(Transaction.Unit.Type.GROSS_VALUE, saleAmount, saleForex,
                                        saleRate));
        shortSale.insert();

        BuySellEntry cover = new BuySellEntry(portfolio, account);
        cover.setType(PortfolioTransaction.Type.BUY);
        cover.setSecurity(security);
        cover.setDate(LocalDateTime.parse("2015-01-09T00:00"));
        cover.setShares(Values.Share.factorize(5));
        Money coverAmount = Money.of(CurrencyUnit.EUR, Values.Amount.factorize(423.26));
        cover.setMonetaryAmount(coverAmount);
        Money coverForex = Money.of(CurrencyUnit.USD, Values.Amount.factorize(500));
        BigDecimal coverRate = BigDecimal.valueOf(coverAmount.getAmount())
                        .divide(BigDecimal.valueOf(coverForex.getAmount()), Values.MC)
                        .setScale(10, RoundingMode.HALF_UP);
        cover.getPortfolioTransaction()
                        .addUnit(new Transaction.Unit(Transaction.Unit.Type.GROSS_VALUE, coverAmount, coverForex,
                                        coverRate));
        cover.insert();

        var interval = Interval.of(LocalDate.parse("2015-01-05"), LocalDate.parse("2015-12-31"));
        SecurityPerformanceSnapshot snapshot = SecurityPerformanceSnapshot.create(client, converter, interval);
        SecurityPerformanceRecord record = snapshot.getRecord(security).orElseThrow(IllegalArgumentException::new);

        CapitalGainsRecord realized = record.getRealizedCapitalGains();
        assertThat(realized.getCapitalGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(38.38))));
        assertThat(realized.getForexCaptialGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(-3.95))));

        CapitalGainsRecord unrealized = record.getUnrealizedCapitalGains();
        assertThat(unrealized.getCapitalGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(43.99))));
        assertThat(unrealized.getForexCaptialGains(),
                        is(Money.of(CurrencyUnit.EUR, Values.Amount.factorize(-7.80))));
    }
}
