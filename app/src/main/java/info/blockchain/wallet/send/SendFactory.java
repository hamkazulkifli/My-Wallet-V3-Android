package info.blockchain.wallet.send;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.spongycastle.util.encoders.Hex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.widget.Toast;
//import android.util.Log;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Transaction.SigHash;
import com.google.bitcoin.params.MainNetParams;

import info.blockchain.wallet.payload.LegacyAddress;
import info.blockchain.wallet.OpCallback;
import info.blockchain.wallet.hd.HD_WalletFactory;
import info.blockchain.wallet.hd.HD_Address;
import info.blockchain.wallet.multiaddr.MultiAddrFactory;
import info.blockchain.wallet.payload.PayloadFactory;
import info.blockchain.wallet.util.ConnectivityStatus;
import info.blockchain.wallet.util.Hash;
import info.blockchain.wallet.util.PrivateKeyFactory;
import info.blockchain.wallet.util.ToastCustom;
import info.blockchain.wallet.util.WebUtil;

import piuk.blockchain.android.R;

/**
 *
 * SendFactory.java : singleton class for spending from Blockchain Android HD wallet
 *
 */
public class SendFactory	{

	private static SendFactory instance = null;
	private static Context context = null;

	private SendFactory () { ; }

	private String[] from = null;
	private HashMap<String,String> froms = null;

    private boolean sentChange = false;

    private static final BigInteger bDust = Utils.toNanoCoins("0.00000546");
    public static final BigInteger bFee = Utils.toNanoCoins("0.0001");

    public static SendFactory getInstance(Context ctx) {
    	
    	context = ctx;
    	
    	if(instance == null)	{
    		instance = new SendFactory();
    	}
    	
    	return instance;
    }

    /**
     * Initial preparation for sending coins from this wallet.
     * <p>
     * Collects sending addresses for HD or legacy spend
     * Collects unspent outputs from sending addresses
     * <p>
     * After calling this method alternate fee amounts may be calculated based
     * on the number of inputs.
     *
     * @param  int accountIdx HD account index, -1 if legacy spend
     * @param  String toAddress Receiving public address
     * @param  BigInteger amount Spending amount (not including fee)
     * @param  LegacyAddress legacyAddress If legacy spend, spend from this LegacyAddress, otherwise null
     * @param  BigInteger fee Miner's fee
     * @param  String note Note to be attached to this tx
     *
     * @return UnspentOutputsBundle
     */
    public UnspentOutputsBundle send1(final int accountIdx, final String toAddress, final BigInteger amount, final LegacyAddress legacyAddress, final BigInteger fee, final String note) {

        final boolean isHD = accountIdx == -1 ? false : true;

        final String xpub;

        if(isHD) {
            xpub = PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(accountIdx).getXpub();

            HashMap<String,List<String>> unspentOutputs = MultiAddrFactory.getInstance().getUnspentOuts();
            List<String> data = unspentOutputs.get(xpub);
            froms = new HashMap<String,String>();
            for(String f : data) {
                if(f != null) {
                    String[] s = f.split(",");
                    // get path info which will be used to calculate private key
                    froms.put(s[1], s[0]);
                }
            }

            from = froms.keySet().toArray(new String[froms.keySet().size()]);
        }
        else {
            xpub = null;

            froms = new HashMap<String,String>();
            from = new String[1];
            from[0] = legacyAddress.getAddress();
        }

        UnspentOutputsBundle ret;
        try {
            if(isHD) {
                ret = getUnspentOutputPoints(true, new String[]{ xpub }, amount.add(fee));
            }
            else {
                ret = getUnspentOutputPoints(false, from, amount.add(fee));
            }
        }
        catch(Exception e) {
            return null;
        }

        if(ret.getOutputs() == null) {
            return null;
        }

        return ret;
    }

    /**
     * Send coins from this wallet.
     * <p>
     * Creates transaction
     * Assigns change address
     * Signs tx
     *
     * @param  int accountIdx HD account index, -1 if legacy spend
     * @param  List<MyTransactionOutPoint> unspent List of unspent outpoints
     * @param  String toAddress Receiving public address
     * @param  BigInteger amount Spending amount (not including fee)
     * @param  LegacyAddress legacyAddress If legacy spend, spend from this LegacyAddress, otherwise null
     * @param  BigInteger fee Miner's fee
     * @param  String note Note to be attached to this tx
     * @param  OpCallback opc
     *
     */
    public void send2(final int accountIdx, final List<MyTransactionOutPoint> unspent, final String toAddress, final BigInteger amount, final LegacyAddress legacyAddress, final BigInteger fee, final String note, final OpCallback opc) {

        final boolean isHD = accountIdx == -1 ? false : true;

        final HashMap<String, BigInteger> receivers = new HashMap<String, BigInteger>();
        receivers.put(toAddress, amount);

        final Handler handler = new Handler();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Looper.prepare();

                    Pair<Transaction, Long> pair = null;
                    String changeAddr = null;
                    if(isHD) {
                        int changeIdx = PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(accountIdx).getIdxChangeAddresses();
                        if(!PayloadFactory.getInstance().get().isDoubleEncrypted()) {
                            changeAddr = HD_WalletFactory.getInstance(context).get().getAccount(accountIdx).getChange().getAddressAt(changeIdx).getAddressString();
                        }
                        else {
                            changeAddr = HD_WalletFactory.getInstance(context).getWatchOnlyWallet().getAccount(accountIdx).getChange().getAddressAt(changeIdx).getAddressString();
                        }
                    }
                    else {
                        changeAddr = legacyAddress.getAddress();
                    }
                    pair = makeTransaction(true, unspent, receivers, fee, changeAddr);
                    // Transaction cancelled
                    if(pair == null) {
                        opc.onFail();
                        return;
                    }
                    Transaction tx = pair.first;
                    Long priority = pair.second;

                    Wallet wallet = new Wallet(MainNetParams.get());
                    for (TransactionInput input : tx.getInputs()) {
                        byte[] scriptBytes = input.getOutpoint().getConnectedPubKeyScript();
                        String address = new BitcoinScript(scriptBytes).getAddress().toString();
                        ECKey walletKey = null;
                        try {
                            String privStr = null;
                            if(isHD) {
                                String path = froms.get(address);
                                String[] s = path.split("/");
                                HD_Address hd_address = null;
                                if(!PayloadFactory.getInstance().get().isDoubleEncrypted()) {
                                    hd_address = HD_WalletFactory.getInstance(context).get().getAccount(accountIdx).getChain(Integer.parseInt(s[1])).getAddressAt(Integer.parseInt(s[2]));
                                }
                                else {
                                    hd_address = HD_WalletFactory.getInstance(context).getWatchOnlyWallet().getAccount(accountIdx).getChain(Integer.parseInt(s[1])).getAddressAt(Integer.parseInt(s[2]));
                                }
                                privStr = hd_address.getPrivateKeyString();
                                walletKey = PrivateKeyFactory.getInstance().getKey(PrivateKeyFactory.WIF_COMPRESSED, privStr);
                            }
                            else {
                                walletKey = legacyAddress.getECKey();
                            }
                        } catch (AddressFormatException afe) {
                            // skip add Watch Only Bitcoin Address key because already accounted for later with tempKeys
                            afe.printStackTrace();
                            continue;
                        }

                        if(walletKey != null) {
                            wallet.addKey(walletKey);
                        }
                        else {
                            opc.onFail();
                        }

                    }

                    // Now sign the inputs
                    tx.signInputs(SigHash.ALL, wallet);
                    String hexString = new String(Hex.encode(tx.bitcoinSerialize()));
                    if(hexString.length() > (100 * 1024)) {
                        opc.onFail();
                        throw new Exception(context.getString(R.string.tx_length_error));
                    }

                    if(ConnectivityStatus.hasConnectivity(context)) {
//					Log.i("SendFactory tx string", hexString);
                        String response = WebUtil.getInstance().postURL(WebUtil.SPEND_URL, "tx=" + hexString);
//					Log.i("Send response", response);
                        if(response.contains("Transaction Submitted")) {

                            opc.onSuccess();

                            if(note != null && note.length() > 0) {
                                Map<String,String> notes = PayloadFactory.getInstance().get().getNotes();
                                notes.put(tx.getHashAsString(), note);
                                PayloadFactory.getInstance().get().setNotes(notes);
                            }

                            if(isHD && sentChange) {
                                // increment change address counter
                                PayloadFactory.getInstance().get().getHdWallet().getAccounts().get(accountIdx).incChange();
                            }

                        }
                        else {
                            ToastCustom.makeText(context, response, ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                            opc.onFail();
                        }
                    }
                    else {
                        ToastCustom.makeText(context, context.getString(R.string.check_connectivity_exit), ToastCustom.LENGTH_SHORT, ToastCustom.TYPE_ERROR);
                    }

//					progress.onSend(tx, response);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            ;
                        }
                    });

                    Looper.loop();

                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Collect unspent outputs for this spend.
     * <p>
     * Collects all unspent outputs for spending addresses,
     * randomizes them, and then selects outputs until amount
     * of selected outputs >= totalAmount
     *
     * @param  boolean isHD true == HD account spend, false == legacy address spend
     * @param  String[] Sending addresses (contains 1 XPUB if HD spend, public address(es) if legacy spend
     * @param  BigInteger totalAmount Amount including fee
     *
     * @return UnspentOutputsBundle
     *
     */
    private UnspentOutputsBundle getUnspentOutputPoints(boolean isHD, String[] from, BigInteger totalAmount) throws Exception {

        BigInteger totalAmountPlusDust = totalAmount.add(bDust);

        UnspentOutputsBundle ret = new UnspentOutputsBundle();

        String args = null;
        if(isHD) {
            args = from[0];
        }
        else {
            StringBuffer buffer = new StringBuffer();
            for(int i = 0; i < from.length; i++) {
                buffer.append(from[i]);
                if(i != (from.length - 1)) {
                    buffer.append("|");
                }
            }

            args = buffer.toString();
        }

        String response = WebUtil.getInstance().getURL(WebUtil.UNSPENT_OUTPUTS_URL + args);
//		Log.i("Unspent outputs", response);

        List<MyTransactionOutPoint> outputs = new ArrayList<MyTransactionOutPoint>();

        Map<String, Object> root = (Map<String, Object>)JSONValue.parse(response);
        List<Map<String, Object>> outputsRoot = (List<Map<String, Object>>)root.get("unspent_outputs");
        if(outputsRoot == null) {
            return null;
        }
        for (Map<String, Object> outDict : outputsRoot) {

            byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));

            Hash hash = new Hash(hashBytes);
            hash.reverse();
            Sha256Hash txHash = new Sha256Hash(hash.getBytes());

            int txOutputN = ((Number)outDict.get("tx_output_n")).intValue();
            BigInteger value = BigInteger.valueOf(((Number)outDict.get("value")).longValue());
            byte[] scriptBytes = Hex.decode((String)outDict.get("script"));
            int confirmations = ((Number)outDict.get("confirmations")).intValue();

            if(isHD) {
                String address = new BitcoinScript(scriptBytes).getAddress().toString();
                String path = null;
                if(outDict.containsKey("xpub")) {
                    JSONObject obj = (JSONObject)outDict.get("xpub");
                    if(obj.containsKey("path")) {
                        path = (String)obj.get("path");
                        froms.put(address, path);
                    }
                }
            }

            // Construct the output
            MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes);
            outPoint.setConfirmations(confirmations);
            // return single output >= totalValue, otherwise save for randomization
            if(outPoint.getValue().compareTo(totalAmountPlusDust) >= 0) {
                outputs.clear();
                outputs.add(outPoint);
                ret.setTotalAmount(outPoint.getValue());
                ret.setOutputs(outputs);
                return ret;
            }
            else {
                outputs.add(outPoint);
            }

        }

        // select the minimum number of outputs necessary
        Collections.sort(outputs, new UnspentOutputAmountComparator());
        List<MyTransactionOutPoint> _outputs = new ArrayList<MyTransactionOutPoint>();
        BigInteger totalValue = BigInteger.ZERO;
        for (MyTransactionOutPoint output : outputs) {
            totalValue = totalValue.add(output.getValue());
            _outputs.add(output);
            if(totalValue.compareTo(totalAmountPlusDust) >= 0) {
                break;
            }
        }

        ret.setTotalAmount(totalValue);
        ret.setOutputs(_outputs);

        return ret;
    }

    /**
     * Creates, populates, and returns transaction instance for this
     * spend and returns it with calculated priority. Change output
     * is positioned randomly.
     *
     * @param  boolean isSimpleSend Always true, not currently used
     * @param  List<MyTransactionOutPoint> unspent Unspent outputs
     * @param  BigInteger amount Spending amount (not including fee)
     * @param  HashMap<String, BigInteger> receivingAddresses
     * @param  BigInteger fee Miner's fee for this spend
     * @param  String changeAddress Change address for this spend
     *
     * @return Pair<Transaction, Long>
     *
     */
	public Pair<Transaction, Long> makeTransaction(boolean isSimpleSend, List<MyTransactionOutPoint> unspent, HashMap<String, BigInteger> receivingAddresses, BigInteger fee, final String changeAddress) throws Exception {

		long priority = 0;

		if(unspent == null || unspent.size() == 0) {
//			throw new InsufficientFundsException("No free outputs to spend.");
			return null;
		}

		if(fee == null) {
			fee = BigInteger.ZERO;
		}

        List<TransactionOutput> outputs = new ArrayList<TransactionOutput>();
		// Construct a new transaction
		Transaction tx = new Transaction(MainNetParams.get());
		BigInteger outputValueSum = BigInteger.ZERO;

		for(Iterator<Entry<String, BigInteger>> iterator = receivingAddresses.entrySet().iterator(); iterator.hasNext();)   {
			Map.Entry<String, BigInteger> mapEntry = iterator.next();
			String toAddress = mapEntry.getKey();
			BigInteger amount = mapEntry.getValue();

			if(amount == null || amount.compareTo(BigInteger.ZERO) <= 0) {
				throw new Exception(context.getString(R.string.invalid_amount));
			}

            if(amount.compareTo(bDust) < 1)    {
                throw new Exception(context.getString(R.string.dust_amount));
            }

            outputValueSum = outputValueSum.add(amount);
			// Add the output
			BitcoinScript toOutputScript = BitcoinScript.createSimpleOutBitcoinScript(new BitcoinAddress(toAddress));
			TransactionOutput output = new TransactionOutput(MainNetParams.get(), null, amount, toOutputScript.getProgram());
            outputs.add(output);
		}

		// Now select the appropriate inputs
		BigInteger valueSelected = BigInteger.ZERO;
		BigInteger valueNeeded =  outputValueSum.add(fee);
		BigInteger minFreeOutputSize = BigInteger.valueOf(1000000);

		MyTransactionOutPoint changeOutPoint = null;

		for(MyTransactionOutPoint outPoint : unspent) {

			BitcoinScript script = new BitcoinScript(outPoint.getScriptBytes());

			if(script.getOutType() == BitcoinScript.ScriptOutTypeStrange) {
				continue;
			}

			BitcoinScript inputScript = new BitcoinScript(outPoint.getConnectedPubKeyScript());
			String address = inputScript.getAddress().toString();

			// if isSimpleSend don't use address as input if is output
			if(isSimpleSend && receivingAddresses.get(address) != null) {
				continue;
			}

			MyTransactionInput input = new MyTransactionInput(MainNetParams.get(), null, new byte[0], outPoint);
			tx.addInput(input);
			valueSelected = valueSelected.add(outPoint.getValue());
			priority += outPoint.getValue().longValue() * outPoint.getConfirmations();

			if(changeAddress == null) {
				changeOutPoint = outPoint;
			}

			if(valueSelected.compareTo(valueNeeded) == 0 || valueSelected.compareTo(valueNeeded.add(minFreeOutputSize)) >= 0) {
				break;
			}
		}

		// Check the amount we have selected is greater than the amount we need
		if(valueSelected.compareTo(valueNeeded) < 0) {
//			throw new InsufficientFundsException("Insufficient Funds");
			return null;
		}

		BigInteger change = valueSelected.subtract(outputValueSum).subtract(fee);
		// Now add the change if there is any
		if (change.compareTo(BigInteger.ZERO) > 0) {
            if(change.compareTo(bDust) <= 0)    {
                throw new Exception(context.getString(R.string.dust_change));
            }
            BitcoinScript change_script;
			if (changeAddress != null) {
				change_script = BitcoinScript.createSimpleOutBitcoinScript(new BitcoinAddress(changeAddress));
                sentChange = true;
			}
            else {
				throw new Exception(context.getString(R.string.invalid_tx));
			}
			TransactionOutput change_output = new TransactionOutput(MainNetParams.get(), null, change, change_script.getProgram());
            outputs.add(change_output);
		}
        else {
            sentChange = false;
        }

        Collections.shuffle(outputs, new SecureRandom());
        for(TransactionOutput to : outputs) {
            tx.addOutput(to);
        }

        long estimatedSize = tx.bitcoinSerialize().length + (114 * tx.getInputs().size());
		priority /= estimatedSize;

		return new Pair<Transaction, Long>(tx, priority);
	}

    /**
     * Sort unspent outputs by amount in descending order.
     *
     */
    private class UnspentOutputAmountComparator implements Comparator<MyTransactionOutPoint> {

        public int compare(MyTransactionOutPoint o1, MyTransactionOutPoint o2) {

            final int BEFORE = -1;
            final int EQUAL = 0;
            final int AFTER = 1;

            int ret = 0;

            if(o1.getValue().compareTo(o2.getValue()) > 0) {
                ret = BEFORE;
            }
            else if(o1.getValue().compareTo(o2.getValue()) < 0) {
                ret = AFTER;
            }
            else    {
                ret = EQUAL;
            }

            return ret;
        }

    }

    private interface SendProgress {

        public void onStart();

        // Return false to cancel
        public boolean onReady(Transaction tx, BigInteger fee, long priority);
        public void onSend(Transaction tx, String message);

        // Return true to cancel the transaction or false to continue without it
        public ECKey onPrivateKeyMissing(String address);

        public void onError(String message);
        public void onProgress(String message);
    }

}
