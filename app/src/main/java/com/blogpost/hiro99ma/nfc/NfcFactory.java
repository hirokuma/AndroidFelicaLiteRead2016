package com.blogpost.hiro99ma.nfc;

import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcF;
import android.os.RemoteException;
import android.util.Log;


/**
 * NFC関係の処理を抜き出した
 * 
 * @author hiroshi
 *
 */
public class NfcFactory {

	private final static String TAG = "NfcFactory";
    
	/*
	 * この辺は必要に応じて変更すること
	 */
    private final static IntentFilter[] mFilters = new IntentFilter[] {
		new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
		new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
	};
    
    private final static String[][] mTechLists = new String[][] {
    	new String[] { Ndef.class.getName() },
    	new String[] { NdefFormatable.class.getName() },
		new String[] { NfcF.class.getName() },
    };

    //空フォーマット用
	private static final NdefMessage mNdefEmpty = new NdefMessage(new NdefRecord(NdefRecord.TNF_EMPTY, null, null, null));

	
    /**
     * onResume()時の動作
     * 
     * @param activity		現在のActivity。だいたいthisを渡すことになる。
     * @return				true:NFCタグ検出の準備ができた<br />
     * 						false:できなかった
     */
	public static boolean nfcResume(Activity activity) {
		//NFC
		NfcManager mng = (NfcManager)activity.getSystemService(Context.NFC_SERVICE);
		if (mng == null) {
			Log.e(TAG, "no NfcManager");
			return false;
		}
		NfcAdapter adapter = mng.getDefaultAdapter();
		if (adapter == null) {
			Log.e(TAG, "no NfcService");
			return false;
		}
		
		//newがnullを返すことはない
		Intent intent = new Intent(activity, activity.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(
						activity,
						0,		//request code
						intent,
						0);		//flagなし
		
		adapter.enableForegroundDispatch(activity, pendingIntent, mFilters, mTechLists);
		
		return true;
	}
	
	/**
	 * onPause()時の動作
	 * 
	 * @param activity		現在のActivity。だいたいthisを渡すことになる。
	 */
	public static void nfcPause(Activity activity) {
		NfcManager mng = (NfcManager)activity.getSystemService(Context.NFC_SERVICE);
		if (mng == null) {
			Log.e(TAG, "no NfcManager");
			return;
		}
		NfcAdapter adapter = mng.getDefaultAdapter();
		if (adapter == null) {
			Log.e(TAG, "no NfcService");
			return;
		}

		if (activity.isFinishing()) {
			adapter.disableForegroundDispatch(activity);
		}
	}

	
	/**
	 * IntentからTagを取得する
	 * 
	 * @param intent		Tag取得対象のIntent
	 * @return				取得したTag。<br />
	 * 						失敗した場合はnullを返す。
	 */
	public static Tag getTag(Intent intent) {
		//チェック
		String action = intent.getAction();
		if (action == null) {
			Log.e(TAG, "fail : null action");
			return null;
		}
		boolean match = false;
		for (IntentFilter filter : mFilters) {
			if (filter.matchAction(action)) {
				match = true;
				break;
			}
		}
		if (!match) {
			Log.e(TAG, "fail : no match intent-filter");
			return null;
		}

		 return (Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	}
	
	
	/**
	 * onNewIntent()で実行したい動作 : NDEFフォーマット(空データ)
	 * 
	 * @param intent		取得したIntent
	 * @return				true:処理成功<br />
	 * 						false:処理失敗
	 */
	public static boolean nfcActionNdefFormat(Intent intent) {
		//Tag取得
		Tag tag = getTag(intent);
		if (tag == null) {
			return false;
		}

		/***********************************************
		 * 以降に、自分がやりたい処理を書く
		 ***********************************************/
		
		boolean ret = false;

		if (Ndef.get(tag) != null) {
			//こいつ、NDEFだ
			ret = ndefFormat(tag);
		} else if (NdefFormatable.get(tag) != null) {
			//こいつ、NDEFフォーマットはされてないけどNDEFフォーマット可能だ
			ret = ndefFormatableFormat(tag);
		} else if (NfcF.get(tag) != null) {
			//こいつ、NFC-Fだ
			ret = felicaLiteFormat(tag, true);
		} else {
			Log.e(TAG, "お前など知らぬ");
			ret = false;
		}
		
		return ret;
	}
	
	
	private static boolean ndefFormat(Tag tag) {
		boolean ret = false;
		Ndef ndef = Ndef.get(tag);
		try {
			ndef.connect();
			ndef.writeNdefMessage(mNdefEmpty);
			ndef.close();
			ret = true;
		} catch (IOException e) {
			Log.e(TAG, "ndefFormat : ioexception");
		} catch (FormatException e) {
			Log.e(TAG, "ndefFormat : formatexception");
		}
		return ret;
	}
	
	private static boolean ndefFormatableFormat(Tag tag) {
		boolean ret = false;
		NdefFormatable ndef = NdefFormatable.get(tag);
		try {
			ndef.connect();
			ndef.format(mNdefEmpty);
			ndef.close();
			ret = true;
		} catch (IOException e) {
			Log.e(TAG, "ndefFormatableFormat : ioexception");
		} catch (FormatException e) {
			Log.e(TAG, "ndefFormatableFormat : formatexception");
		}
		return ret;
	}
	
	private static boolean felicaLiteFormat(Tag tag, boolean isNdef) {
		boolean ret = false;
		FelicaLite felica = null;
		try {
			felica = FelicaLite.get(tag);
			if (felica == null) {
				Log.e(TAG, "felicaLiteFormat : no felica lite");
				return false;
			}

			felica.connect();
			ret = felica.polling(FelicaLite.SC_FELICALITE);
			if (!ret) {
				Log.d(TAG, "felicaLiteFormat : polling");
			}
			
			if(ret) {
				if (isNdef) {
					ret = felica.format(mNdefEmpty);
				} else {
					ret = felica.rawFormat();
				}
			}

		} catch (IOException e) {
			Log.e(TAG, "felicaLiteFormat : format");
			ret = false;
		} catch (RemoteException e) {
			Log.e(TAG, "felicaLiteFormat : felica lite");
			ret = false;
		}
		if (felica != null) {
			try {
				felica.close();
			} catch (IOException e) {
				Log.e(TAG, "felicaLiteFormat : close");
				ret = false;
			}
		}
		
		return ret;
	}
	
	/**
	 * onNewIntent()で実行したい動作 : 非NDEFフォーマット(可能な場合)
	 * 
	 * @param intent		取得したIntent
	 * @return				true:処理成功<br />
	 * 						false:処理失敗
	 */
	public static boolean nfcActionRawFormat(Intent intent) {
		//Tag取得
		Tag tag = getTag(intent);
		if (tag == null) {
			return false;
		}

		/***********************************************
		 * 以降に、自分がやりたい処理を書く
		 ***********************************************/
		
		boolean ret = false;

		if (NfcF.get(tag) != null) {
			//こいつ、NFC-Fだ
			ret = felicaLiteFormat(tag, false);
		} else if (MifareUltralight.get(tag) != null) {
			//こいつ、MIFARE Ultralightだ
			ret = mifareUlRawFormat(tag);
		} else {
			Log.e(TAG, "お前など知らぬ");
			ret = false;
		}
		
		return ret;
	}
	
	
	/**
	 * MIFARE Ultralightを空NDEFフォーマットする。<br />
	 * OTPにNDEF値が入ると、AndroidでNdefFormatable#format()が失敗することがあるため、空NDEF TLVを作っている。
	 * 
	 * @param tag		MifareUltralight
	 * @return			true:処理成功 / false:処理失敗
	 */
	private static boolean mifareUlRawFormat(Tag tag) {
		boolean ret = false;
		MifareUltralight mifare = MifareUltralight.get(tag);
		try {
			mifare.connect();
			
			byte[] clr = new byte[4];
			
			clr[0] = (byte)0x03;		//TLV:NDEF
			clr[1] = (byte)0x00;		//length
			clr[2] = (byte)0xfe;		//TLV:Terminator
			mifare.writePage(4, clr);
			
			clr[0] = 0x00;
			clr[2] = 0x00;

			for (int blk = 5; blk < 16; blk++) {
				mifare.writePage(blk, clr);
			}
			
			ret = true;
		} catch (IOException e) {
			Log.e(TAG, "mifareUlFormat : ioexception");
		}
		
		//領域外かもしれないので、ここでの失敗は気にしない
		if (ret) {
			try {
				byte[] clr = new byte[4];
				
				for (int blk = 16; blk < 40; blk++) {
					mifare.writePage(blk, clr);
				}
			} catch (IOException e) {
				Log.e(TAG, "mifareUlFormat : ioexception(don't worry)");
			}
				
			try {
				mifare.close();
			} catch (IOException e) {
				Log.e(TAG, "mifareUlFormat : close");
			}
		}
		
		return ret;
	}
}
