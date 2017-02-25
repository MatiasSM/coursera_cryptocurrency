import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.stream.IntStream;

/**
 * Note: Currently implemented tests only verify {@code handleTxs} method (assuming it uses {@code isValid} as a way of
 * validating txs.
 * TODO: validate that internal UTXOPool is updated
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( { Crypto.class })
public class TxHandlerTest {

    private TxHandlerTestHelper.HandleTxsRunner txHandlerRunner =
        (utxoPool, txs) -> new TxHandler(utxoPool).handleTxs(txs);

    @Test
    public void correctlyChecksValidTxOnlyInput() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
            .and()
            .newBlockTx(2)
                .withInput(20).fromPreExistingTx().andNewOutputIdx()
            .and()
            .expectTxsInResult(1, 2)
            .test(txHandlerRunner);
    }

    @Test
    public void correctlyChecksValidTxInputOutput() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
                .withOutput(8)
            .and()
            .newBlockTx(2)
                .withInput(20).fromPreExistingTx().andNewOutputIdx()
                .withOutput(15)
                .withOutput(5)
            .and()
            .expectTxsInResult(1, 2)
            .test(txHandlerRunner);
    }


    @Test
    public void correctlyChecksValidTxInBlockRef() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
                .withOutput(8)
            .and()
            .newBlockTx(2)
                .withInput(8).fromTx(1).andOutputIdx(0)
                .withOutput(8)
            .and()
            .expectTxsInResult(1, 2)
            .test(txHandlerRunner);
    }

    @Test
    public void detectsTxWithInvalidKey() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andInvalidKeyOutput()
            .and()
            .expectNoTxInResult()
            .test(txHandlerRunner);
    }


    @Test
    public void detectsTxWithInvalidRefIdx() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andInvalidIdx()
            .and()
            .expectNoTxInResult()
            .test(txHandlerRunner);
    }

    @Test
    public void detectsTxWithInvalidRefIdxForInBlockRef() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
            .and()
            .newBlockTx(2)
                .withInput(8).fromTx(1).andInvalidIdx()
            .and()
            .expectTxsInResult(1)
            .test(txHandlerRunner);
    }

    @Test
    public void detectsTxWithExcessiveOutput() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
                .withOutput(11)
            .and()
            .expectNoTxInResult()
            .test(txHandlerRunner);
    }

    @Test
    public void detectsUTXOPoolDoubleSpend() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
            .and()
            .newBlockTx(2)
                .withInput(8).fromPreExistingTx().andOutputIdx(0) //Note: idx created by andNewOutputIdx for prev tx
            .and()
            //just one of the 2 can be considered valid
            .addValidResult(1)
            .addValidResult(2)
            .test(txHandlerRunner);
    }

    @Test
    public void detectsInBlockRefDoubleSpend() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
                .withOutput(10)
            .and()
            .newBlockTx(2)
                .withInput(8).fromTx(1).andOutputIdx(0)
            .and()
            .newBlockTx(3)
                .withInput(2).fromTx(1).andOutputIdx(0)
            .and()
            //just one of the 2 can be considered valid
            .addValidResult(1, 2)
            .addValidResult(1, 3)
            .test(txHandlerRunner);
    }

    @Test
    public void detectsTxWithNegativeOutput() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromPreExistingTx().andNewOutputIdx()
                .withOutput(-1)
            .and()
            .expectNoTxInResult()
            .test(txHandlerRunner);
    }


    @Test
    public void detectsTxWithRefToNonExistentPreTx() {
        new TxHandlerTestHelper()
            .newBlockTx(1)
                .withInput(10).fromInvalidPreExistingTx()
            .and()
            .expectNoTxInResult()
            .test(txHandlerRunner);
    }

    @Test(timeout = 5000)
    public void supportsManyValidTx() {
        int txCount = 300;
        Integer[] txIdList = from1to(txCount);

        TxHandlerTestHelper testHelper = new TxHandlerTestHelper();
        for (Integer id : txIdList) {
            testHelper = testHelper.newBlockTx(id).withInput(5 + id).fromPreExistingTx().andNewOutputIdx().and();
        }
        testHelper.expectTxsInResult(txIdList)
            .test(txHandlerRunner);
    }

    @Test(timeout = 5000)
    public void supportsManyValidTxWithInBlockRefs() {
        double inputValue = 1000;
        int txCount = 300;
        Integer[] txIdList = from1to(txCount);

        TxHandlerTestHelper testHelper = new TxHandlerTestHelper();
        testHelper = testHelper
            .newBlockTx(txIdList[0]).withInput(inputValue).fromPreExistingTx().andNewOutputIdx().and();
        for (int i = 1; i < txIdList.length; i++) {
            Integer id = txIdList[i];
            testHelper = testHelper.newBlockTx(id)
                .withInput(inputValue)
                .fromTx(id - 1) //refer to previous tx
                .andNewOutputIdx()
                .and();
        }
        testHelper.expectTxsInResult(txIdList)
            .test(txHandlerRunner);
    }

    private Integer[] from1to(int maxValue) {
        return IntStream.rangeClosed(1, maxValue).boxed().toArray(Integer[]::new);
    }
}
