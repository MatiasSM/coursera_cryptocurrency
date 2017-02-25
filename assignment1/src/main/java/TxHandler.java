import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.ByteBuffer.wrap;

@SuppressWarnings("WeakerAccess")
public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        return isValidTx(tx, utxoPool);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        /*
        Note: this implementation is intended to be clear not performing.

        Starts with a ValidationCtx with empty txIds and the current UTXOPool.
        For each Transaction, check if it is valid.
        To validate a transaction, the ValidationCtx is used. If the required tx is in the Ctx, nothing needs to be
        done and the same context is returned. Otherwise, recurse to check all in-block TXs required for this to be
        valid. Since a tx may be valid for one context but not for another, the TXs need to be checked each time.

        Possible optimizations, check if there is an unrecoverable error (bad signature, negative output, etc) to avoid
        double checking a tx.
         */

        Map<ByteBuffer, Transaction> idToTx =
            Arrays.stream(possibleTxs).collect(Collectors.toMap(t -> wrap(t.getHash()), Function.identity()));

        ValidationCtx ctx = new ValidationCtx(Collections.emptySet(), utxoPool);
        List<Transaction> accepted = new ArrayList<>(possibleTxs.length);

        for (Transaction possibleTx : possibleTxs) {
            try {
                ValidationCtx newValidCtx = generateValidationContext(possibleTx, ctx, idToTx);
                accepted.add(possibleTx);
                ctx = newValidCtx;
            } catch (InvalidTxException e) {
                //invalid tx will be ignored
            }
        }

        utxoPool = ctx.utxoPool;
        return accepted.stream().toArray(Transaction[]::new);
    }

    // ------------------------------------------------------------------
    private static class InvalidTxException extends Exception {}

    private static class ValidationCtx {
        private final Set<ByteBuffer> txIds;
        private final UTXOPool utxoPool;

        ValidationCtx(Set<ByteBuffer> txIds, UTXOPool utxoPool) {
            this.txIds = txIds;
            this.utxoPool = utxoPool;
        }
    }

    private ValidationCtx generateValidationContext(
        Transaction possibleTx, ValidationCtx ctx, Map<ByteBuffer, Transaction> idToTx)
        throws InvalidTxException
    {
        //nothing to be done, the TX is already valid for this ctx
        if (ctx.txIds.contains(wrap(possibleTx.getHash()))) return ctx;

        ValidationCtx newValidCtx = ctx;
        for (Transaction.Input input : possibleTx.getInputs()) {
            Transaction txInBlock = idToTx.get(wrap(input.prevTxHash));
            if (txInBlock != null) {
                /* possibleTx can only be valid if the TX for the input is also valid
                 * Note: due to causality of transactions I assume there are no loops in the dependency graph between
                 * in-block transactions (additional state would validate this assumption)
                 */
                newValidCtx = generateValidationContext(txInBlock, newValidCtx, idToTx);
            }
        }

        //verify this TX is valid
        if (! isValidTx(possibleTx, newValidCtx.utxoPool)) {
            throw new InvalidTxException();
        }

        //update UTXOPool to generate resulting validation context
        UTXOPool resultPool = new UTXOPool(newValidCtx.utxoPool);
        for (Transaction.Input input : possibleTx.getInputs()) {
            resultPool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
        }
        for (int i = 0; i < possibleTx.getOutputs().size(); i++) {
            resultPool.addUTXO(new UTXO(possibleTx.getHash(), i), possibleTx.getOutput(i));
        }
        Set<ByteBuffer> newTxIds = new HashSet<>(newValidCtx.txIds);
        newTxIds.add(wrap(possibleTx.getHash()));

        return new ValidationCtx(newTxIds, resultPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the {@code utxoPool},
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    private static boolean isValidTx(Transaction tx, UTXOPool utxoPool) {
        double inputSumRemaining = 0;
        Set<UTXO> claimedUtxos = new HashSet<>();
        for (int i = 0; i < tx.getInputs().size(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);

            //validate (1)
            if (! utxoPool.contains(utxo))
                return false;

            //validate (3)
            if (! claimedUtxos.add(utxo)) {
                return false;
            }

            //validate (2)
            if (! Crypto.verifySignature(utxoPool.getTxOutput(utxo).address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }

            //to validate (5)
            Transaction.Output correspondingOutput = utxoPool.getTxOutput(utxo);
            inputSumRemaining += correspondingOutput.value;
        }

        for (Transaction.Output output : tx.getOutputs()) {
            //validate (4)
            if (output.value < 0)
                return false;

            //to validate (5)
            inputSumRemaining -= output.value;
        }
        //validate (5)
        return inputSumRemaining >= 0;
    }
}
