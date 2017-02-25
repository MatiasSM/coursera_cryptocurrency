/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.powermock.api.mockito.PowerMockito;

import java.math.BigInteger;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/** Helper class to test transaction block processing. It gives a fluent, human-readable interface to set-up the
 * state and expected result. */
class TxHandlerTestHelper {
    interface HandleTxsRunner {
        /** Executes the handleTx to process {@code txs} using the initial state {@code utxoPool}.
         * @return same as defined by {@link TxHandler#handleTxs(Transaction[])}. */
        Transaction[] handleFor(UTXOPool utxoPool, Transaction[] txs);
    }

    private static final Transaction preExistentTx = createTx(-1);
    private final PublicKey validKey;
    private final PublicKey invalidKey;
    private Map<Integer, Transaction> blockTxs;
    private List<Set<Integer>> expectedResultTxs;

    TxHandlerTestHelper() {
        mockStatic(Crypto.class);
        validKey = newPublicKey(true);
        invalidKey = newPublicKey(false);
        reset();
    }

    TxBuilder newBlockTx(int txId) {
        Transaction newTx = createTx(txId);
        blockTxs.put(txId, newTx);
        return new TxBuilder(newTx);
    }

    TxHandlerTestHelper expectNoTxInResult() {
        expectedResultTxs.clear();
        addValidResult(); //empty list
        return this;
    }

    TxHandlerTestHelper expectTxsInResult(Integer...txIds) {
        expectedResultTxs.clear();
        addValidResult(txIds);
        return this;
    }

    TxHandlerTestHelper addValidResult(Integer...txIds) {
        expectedResultTxs.add(Arrays.stream(txIds).collect(Collectors.toSet()));
        return this;
    }

    TxHandlerTestHelper test(HandleTxsRunner runner) {
        UTXOPool utxoPool = new UTXOPool();
        //all outputs of preExistentTx are UTXO
        for (int i = 0; i < preExistentTx.getOutputs().size(); i++) {
            utxoPool.addUTXO(new UTXO(preExistentTx.getHash(), i), preExistentTx.getOutputs().get(i));
        }

        Transaction[] result = runner.handleFor(utxoPool, blockTxs.values().stream().toArray(Transaction[]::new));
        PowerMockito.verifyStatic();

        Set<Integer> resultIds =
            Arrays.stream(result).map(Transaction::getHash).map(TxHandlerTestHelper::toTxIdx).collect(Collectors.toSet());

        //validate the resulting ids are present in the expected result list
        boolean resultFound = false;
        for (Set<Integer> expectedResultTx : expectedResultTxs) {
            if (result.length == resultIds.size() && expectedResultTx.equals(resultIds)) {
                resultFound = true;
                break;
            }
        }
        assertTrue("Actual result (not found): " + resultIds, resultFound);
        return this;
    }

    private TxHandlerTestHelper reset() {
        blockTxs = new HashMap<>();
        expectedResultTxs = new ArrayList<>();
        return this;
    }

    private static Transaction createTx(int idx) {
        Transaction tx = new Transaction();
        tx.setHash(toTxHash(idx));
        return tx;
    }

    private static byte[] toTxHash(int idx) {
        return BigInteger.valueOf(idx).toByteArray();
    }

    private static int toTxIdx(byte[] hash) {
        return new BigInteger(hash).intValue();
    }

    private PublicKey newPublicKey(boolean isValid) {
        try {
            PublicKey result = PowerMockito.mock(PublicKey.class);
            when(result, "getEncoded").thenReturn(new byte[]{});
            when(Crypto.verifySignature(eq(result), any(byte[].class), any(byte[].class))).thenReturn(isValid);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    class TxBuilder {
        private final Transaction newTx;

        TxBuilder(Transaction newTx) {
            this.newTx = newTx;
        }

        TxBuilder.InputBuilder withInput(double value) {
            return new TxBuilder.InputBuilder(value);
        }

        TxBuilder withOutput(double value) {
            newTx.addOutput(value, validKey);
            return this;
        }

        /** "closes" the TxBuilder */
        TxHandlerTestHelper and() {
            return TxHandlerTestHelper.this;
        }

        class InputBuilder {
            private final double value;
            private Transaction prevTx = null;

            InputBuilder(double value) {
                this.value = value;
            }

            TxBuilder.InputBuilder fromTx(int txIdx) {
                prevTx = blockTxs.get(txIdx);
                return this;
            }

            TxBuilder.InputBuilder fromPreExistingTx() {
                prevTx = preExistentTx;
                return this;
            }

            TxBuilder fromInvalidPreExistingTx() {
                newTx.addInput(new byte[]{7,7,7}, 0);
                return TxBuilder.this;
            }

            TxBuilder andInvalidKeyOutput() {
                prevTx.addOutput(value, invalidKey);

                addInput(prevTx.getOutputs().size() - 1);
                return TxBuilder.this;
            }

            TxBuilder andInvalidIdx() {
                addInput(1000); //we assume the test won't add this amount of outputs to the referring tx
                return TxBuilder.this;
            }

            TxBuilder andNewOutputIdx() {
                prevTx.addOutput(value, validKey);

                addInput(prevTx.getOutputs().size() - 1);
                return TxBuilder.this;
            }

            /** closes the TxBuilder */
            TxBuilder andOutputIdx(int prevOutputIdx) {
                assert prevTx.getOutput(prevOutputIdx) != null;
                addInput(prevOutputIdx);
                return TxBuilder.this;
            }

            /** Unsafe method which doesn't do any validation, just adds the specified input */
            TxBuilder from(int txId, int outputIdx) {
                newTx.addInput(toTxHash(txId), outputIdx);
                return TxBuilder.this;
            }

            private void addInput(int outputIdx) {
                newTx.addInput(prevTx.getHash(), outputIdx);
            }
        }
    }
}
