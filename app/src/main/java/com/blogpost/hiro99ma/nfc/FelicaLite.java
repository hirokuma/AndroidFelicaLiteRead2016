package com.blogpost.hiro99ma.nfc;

import java.io.IOException;
import java.util.Arrays;

import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.RemoteException;
import android.util.Log;


/**
 * @class	FelicaLite
 * @brief	FeliCa Lite card access
 */
public class FelicaLite {
	public static final int SC_BROADCAST = 0xffff;
	public static final int SC_FELICALITE = 0x88b4;
	public static final int SC_NFCF = 0x12fc;

	public static final int PAD0 = 0x0000;
	public static final int PAD1 = 0x0001;
	public static final int PAD2 = 0x0002;
	public static final int PAD3 = 0x0003;
	public static final int PAD4 = 0x0004;
	public static final int PAD5 = 0x0005;
	public static final int PAD6 = 0x0006;
	public static final int PAD7 = 0x0007;
	public static final int PAD8 = 0x0008;
	public static final int PAD9 = 0x0009;
	public static final int PAD10 = 0x000a;
	public static final int PAD11 = 0x000b;
	public static final int PAD12 = 0x000c;
	public static final int PAD13 = 0x000d;
	public static final int REG = 0x000e;
	public static final int RC = 0x0080;
	public static final int MAC = 0x0081;
	public static final int ID = 0x0082;
	public static final int D_ID = 0x0083;
	public static final int SER_C = 0x0084;
	public static final int SYS_C = 0x0085;
	public static final int CKV = 0x0086;
	public static final int CK = 0x0087;
	public static final int MC = 0x0088;

	public static final int SIZE_BLOCK = 16;

	private static final String TAG = "FelicaLite";

	private Tag mTag;
	private NfcF mNfcF;


	private FelicaLite() throws RemoteException {
		;
	}

	public static FelicaLite get(Tag tag) throws RemoteException {
		NfcF nfcf = NfcF.get(tag);
		if (nfcf.isConnected()) {
			//connect済み
			Log.e(TAG, "connect : already connected");
			return null;
		}

		//NFC-Fチェック
		boolean chk = false;
		String[] techlist = tag.getTechList();
		for (String tl : techlist) {
			if (tl.equals(NfcF.class.getName())) {
				chk = true;
				break;
			}
		}
		if (!chk) {
			Log.e(TAG, "connect : not NFC-F");
			return null;
		}

		FelicaLite me = new FelicaLite();
		me.mTag = tag;
		me.mNfcF = nfcf;

		return me;
	}

	/**
	 * 使用する場合、最初に呼び出す。
	 * 内部で{@link NfcF#connect()}を呼び出す。
	 * 呼び出し場合、最後に{@link FelicaLite#close()}を呼び出すこと。
	 *
	 * {@link FelicaLite#close()}が呼ばれるまでtagをキャッシュする。
	 *
	 * @param[in]	tag		intentで取得したTag
	 * @return		NfcF
	 * @throws IOException
	 * @see		{@link FelicaLite#close()}
	 */
	public void connect() throws IOException {
		if (mNfcF == null) {
			throw new IOException();
		}
		mNfcF.connect();
	}


	/**
	 * {@link #connect()}を呼び出したかどうかのチェック
	 *
	 * @return	true	呼び出している
	 */
	public boolean isConnected() {
		if (mNfcF == null) {
			return false;
		}
		return mNfcF.isConnected();
	}


	/**
	 * {@link FelicaLite#connect()}を呼び出したら、最後に呼び出すこと。
	 * 内部で{@link NfcF#close()}を呼び出す。
	 *
	 * @throws IOException
	 * @see		{@link FelicaLite#connect()}
	 * @note	- {@link FelicaLite#connect()}でキャッシュしたtagを解放する
	 */
	public void close() throws IOException {
		if (isConnected()) {
			mNfcF.close();
		}
		mTag = null;
		mNfcF = null;
	}

	public byte[] getManufacturer() {
		return mNfcF.getManufacturer();
	}

	public int getMaxTransceiveLength() {
		return mNfcF.getMaxTransceiveLength();
	}

	public byte[] getSystemCode() {
		return mNfcF.getSystemCode();
	}

	public Tag getTag() {
		return (Tag)mTag;
	}

	public int getTimeout() {
		return mNfcF.getTimeout();
	}

	void setTimeout(int timeout) {
		mNfcF.setTimeout(timeout);
	}

	byte[] transceive(byte[] data) throws IOException {
		return mNfcF.transceive(data);
	}


	/**
	 * ポーリング(うまく動いてない？)
	 *
	 * {@link FelicaLite#connect()}を呼び出しておくこと。
	 *
	 * @param sc			[in]サービスコード
	 * @return				true	ポーリング成功
	 * @throws IOException
	 */
	public boolean polling(int sc) throws IOException {
		byte[] buf = new byte[6];
		buf[0] = 6;
		buf[1] = 0x00;
		buf[2] = (byte)((sc & 0xff00) >> 8);
		buf[3] = (byte)(sc & 0xff);
		buf[4] = 0x00;
		buf[5] = 0x00;

		byte[] ret = mNfcF.transceive(buf);

		//length check
		if(ret.length != 18) {
			Log.e(TAG, "polling : length");
			return false;
		}
		//IDm check
		byte[] idm = mTag.getId();
		for(int i=0; i<8; i++) {
			if(ret[i+2] != idm[i]) {
				Log.e(TAG, "polling : nfcid");
				return false;
			}
		}
		//response code check
		if(ret[1] != 0x01) {
			Log.e(TAG, "polling : response code");
			return false;
		}

		return true;
	}


	/**
	 * 1ブロック書込み
	 *
	 * {@link FelicaLite#connect()}を呼び出しておくこと。
	 *
	 * @param blockNo		[in]書込対象のブロック番号
	 * @param data			[in]書き込みデータ(先頭の16byteを使用)
	 * @return		true	書込成功
	 * @throws IOException
	 */
	public boolean writeBlock(int blockNo, byte[] data) throws IOException {
		if((data == null) || (data.length < 16)) {
			//データ不正
			Log.e(TAG, "writeBlock : param");
			return false;
		}

		byte[] buf = new byte[32];
		buf[0] = 32;					//length
		buf[1] = (byte)0x08;			//Write Without Encryption
		System.arraycopy(mTag.getId(), 0, buf, 2, 8);
		buf[10] = (byte)0x01;			//service num
		buf[11] = (byte)0x09;			//service code list(lower)
		buf[12] = (byte)0x00;			//service code list(upper)
		buf[13] = (byte)0x01;			//blocklist num
		buf[14] = (byte)0x80;			//2byte-blocklist(upper)
		buf[15] = (byte)blockNo;		//2byte-blocklist(lower)
		System.arraycopy(data, 0, buf, 16, SIZE_BLOCK);

		byte[] ret = mNfcF.transceive(buf);

		//length check
		if(ret.length != 12) {
			Log.e(TAG, "writeBlock : length");
			return false;
		}
		//IDm check
		for(int i=2+0; i<2+8; i++) {
			if(ret[i] != buf[i]) {
				Log.e(TAG, "writeBlock : nfcid");
				return false;
			}
		}
		//status flag check
		if((ret[1] != 0x09) || (ret[10] != 0x00) || (ret[11] != 0x00)) {
			Log.e(TAG, "writeBlock : status");
			return false;
		}
		return true;
	}


	/**
	 * 1ブロック読み込み<br>
	 * <br>
	 * {@link FelicaLite#connect()}を呼び出しておくこと。
	 *
	 * @param blockNo		[in]読込対象のブロック番号
	 * @return				(!=null)読み込んだ1ブロックデータ / (==null)エラー
	 * @throws IOException
	 */
	public byte[] readBlock(int blockNo) throws IOException {
		byte[] buf = new byte[16];
		buf[0] = 16;					//length
		buf[1] = (byte)0x06;			//Read Without Encryption
		System.arraycopy(mTag.getId(), 0, buf, 2, 8);
		buf[10] = (byte)0x01;			//service num
		buf[11] = (byte)0x0b;			//service code list(lower)
		buf[12] = (byte)0x00;			//service code list(upper)
		buf[13] = (byte)0x01;			//blocklist num
		buf[14] = (byte)0x80;			//2byte-blocklist(upper)
		buf[15] = (byte)blockNo;		//2byte-blocklist(lower)

		byte[] ret = mNfcF.transceive(buf);

		//length check
		if(ret.length != 29) {
			Log.e(TAG, "readBlock : length");
			return null;
		}
		//IDm check
		for(int i=2+0; i<2+8; i++) {
			if(ret[i] != buf[i]) {
				Log.e(TAG, "readBlock : nfcid");
				return null;
			}
		}
		//status flag check
		if((ret[1] != 0x07) || (ret[10] != 0x00) || (ret[11] != 0x00)) {
			Log.e(TAG, "readBlock : status");
			return null;
		}

		//read data copy
		//(buf.lengthが16なので、使い回ししている)
		System.arraycopy(ret, 13, buf, 0, SIZE_BLOCK);
		return buf;
	}

	/**
	 * nブロック読み込み<br>
	 * <br>
	 * - {@link FelicaLite#connect()}を呼び出しておくこと。<br>
	 * - blockNo.lengthが4より大きい場合、先頭の4つを使用する。<br>
	 *
	 * @param blockNo		[in]読込対象のブロック番号(4つまで)
	 * @return				(!=null)読み込んだブロックデータ(blockNoの順) / (==null)エラー
	 * @throws IOException
	 */
	public byte[] readBlock(int[] blockNo) throws IOException {
		int num = blockNo.length;
		if(num > 4) {
			//FeliCa Lite limit
			Log.w(TAG, "readBlocks : 4blocks limit");
			num = 4;
		}
		byte[] buf = new byte[14 + num * 2];
		buf[0] = (byte)(14 + num * 2);	//length
		buf[1] = (byte)0x06;			//Read Without Encryption
		System.arraycopy(mTag.getId(), 0, buf, 2, 8);
		buf[10] = (byte)0x01;			//service num
		buf[11] = (byte)0x0b;			//service code list(lower)
		buf[12] = (byte)0x00;			//service code list(upper)
		buf[13] = (byte)num;			//blocklist num
		for(int loop=0; loop<num; loop++) {
			buf[14 + loop * 2]     = (byte)0x80;			//2byte-blocklist(upper)
			buf[14 + loop * 2 + 1] = (byte)blockNo[loop];	//2byte-blocklist(lower)
		}

		byte[] ret = mNfcF.transceive(buf);

		//length check
		if(ret.length != 13 + num * SIZE_BLOCK) {
			Log.e(TAG, "readBlocks : length");
			return null;
		}
		//IDm check
		for(int i=2+0; i<2+8; i++) {
			if(ret[i] != buf[i]) {
				Log.e(TAG, "readBlocks : nfcid");
				return null;
			}
		}
		//status flag check
		if((ret[1] != 0x07) || (ret[10] != 0x00) || (ret[11] != 0x00) || (ret[12] != num)) {
			Log.e(TAG, "readBlocks : status");
			return null;
		}

		//read data copy
		byte[] res = new byte[num * SIZE_BLOCK];
		System.arraycopy(ret, 13, res, 0, num * SIZE_BLOCK);
		return res;
	}


	/**
	 * NDEFフォーマット
	 * <br>
	 * - {@link FelicaLite#connect()}を呼び出しておくこと。<br>
	 *
	 * @param	firstMessage	書き込むNDEFメッセージ
	 * @return				true:成功 / false:失敗
	 * @throws IOException
	 */
	public boolean format(NdefMessage firstMessage) throws IOException {
		if (!isConnected()) {
			Log.e(TAG, "format : not connect");
			return false;
		}

		//FeliCa Lite check
		if (!chkFelicaLite()) {
			Log.e(TAG, "format : not FeliCa Lite");
			return false;
		}

		boolean ret = false;
		byte[] raw_data = null;

		//MC
		byte[] mc = readBlock(MC);
		if(mc != null) {
			//System Code chg
			mc[3] = 0x01;

			ret = writeBlock(MC, mc);
			if (ret) {
				//Write T3T header
				byte[] t3t = new byte[] {
								0x10,			//Ver
								0x04,			//Nbr
								0x01,			//Nbw
								0x00, 0x0d,		//Nmaxb
								0x00, 0x00, 0x00, 0x00,
								0x00,			//WriteF
								0x01,			//RW
								0x00, 0x00, 0x00,		//Ln
								0x00, 0x23,		//Checksum
				};
				if (firstMessage != null) {
					raw_data = firstMessage.toByteArray();
					int len = raw_data.length;
					if (len <= 208) {
						t3t[0x0d] = (byte)len;
						int chksum = 0x23 + len;
						t3t[0x0e] = (byte)(chksum >> 8);
						t3t[0x0f] = (byte)(chksum & 0xff);
					} else {
						Log.w(TAG, "format : too large ndef");
						raw_data = null;
					}
				}
				ret = writeBlock(PAD0, t3t);
				if (ret) {
					int blks = 0;
					byte[] clr = new byte[16];
					
					if (raw_data != null) {
						//NDEF初期メッセージ
						blks = (raw_data.length + 15) % 16;
						for (int blk = 0; blk < blks; blk++) {
							int cpylen;
							if (blk == blks - 1) {
								//最後
								cpylen = raw_data.length - blk * 16;
								Arrays.fill(clr, (byte)0x00);
							} else {
								cpylen = 16;
							}
							System.arraycopy(raw_data, blk * 16, clr, 0, cpylen);
							writeBlock(PAD1 + blk, clr);
						}
						
						// zero clear
						Arrays.fill(clr, (byte)0x00);
					}
					
					//erase rest bytes
					for (int blk = blks; blk < 13; blk++) {
						//エラーチェックしない
						writeBlock(PAD1 + blk, clr);
					}
				} else {
					Log.e(TAG, "format : write Header");
				}

			} else {
				Log.e(TAG, "format : write MC");
			}
		} else {
			Log.e(TAG, "format : read MC");
		}

		return ret;
	}



	/**
	 * 非NDEFフォーマット(1次発行前の場合)<br />
	 * 
	 * - {@link FelicaLite#connect()}を呼び出しておくこと。<br>
	 *
	 * @return				true:成功 / false:失敗
	 * @throws IOException
	 */
	public boolean rawFormat() throws IOException {
		if (!isConnected()) {
			Log.e(TAG, "rawFormat : not connect");
			return false;
		}

		//FeliCa Lite check
		if (!chkFelicaLite()) {
			Log.e(TAG, "rawFormat : not FeliCa Lite");
			return false;
		}

		boolean ret = false;

		//MC
		byte[] mc = readBlock(MC);
		if(mc != null) {
			//System Code chg
			mc[3] = 0x00;

			ret = writeBlock(MC, mc);
			if (ret) {
				//erase rest bytes
				byte[] clr = new byte[16];
				for (int blk = PAD0; blk <= PAD13; blk++) {
					//エラーチェックしない
					writeBlock(blk, clr);
				}

			} else {
				Log.e(TAG, "rawFormat : write MC");
			}
		} else {
			Log.e(TAG, "rawFormat : read MC");
		}

		return ret;
	}
	
	private boolean chkFelicaLite() {
		//System Code check
		//本当ならここで0x88b4に対してpolling()したかったのだが、
		//なぜかシステムエラーが発生してしまう。
		//よってここでは、Android側はポーリングをブロードキャストしている前提とした。
		byte[] sc = mNfcF.getSystemCode();
		if ((sc[0] != (byte)0x88) || (sc[1] != (byte)0xb4)) {
			return false;
		}
		
		return true;
	}
}
