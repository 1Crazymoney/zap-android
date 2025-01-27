package zapsolutions.zap.backup;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import zapsolutions.zap.connection.manageWalletConfigs.WalletConfig;
import zapsolutions.zap.connection.manageWalletConfigs.WalletConfigsManager;
import zapsolutions.zap.contacts.Contact;
import zapsolutions.zap.contacts.ContactsManager;
import zapsolutions.zap.util.EncryptionUtil;
import zapsolutions.zap.util.RefConstants;
import zapsolutions.zap.util.UtilFunctions;

public class DataBackupUtil {

    public static final String BACKUP_FILE_IDENTIFIER = "ZapBackup:";

    public static byte[] createBackup(String password, int backupVersion) {
        String backupJson = "{";
        // Contacts
        if (ContactsManager.getInstance().hasAnyContacts()) {
            String contactsJsonString = new Gson().toJson(ContactsManager.getInstance().getContactsJson());
            contactsJsonString = contactsJsonString.substring(1, contactsJsonString.length() - 1);
            backupJson = backupJson + contactsJsonString + ",";
        }
        // Wallets
        if (WalletConfigsManager.getInstance().hasAnyConfigs()) {
            String walletsJsonString = new Gson().toJson(WalletConfigsManager.getInstance().getWalletConfigsJson());
            walletsJsonString = walletsJsonString.substring(1, walletsJsonString.length() - 1);
            backupJson = backupJson + walletsJsonString + ",";
        }
        backupJson = backupJson.substring(0, backupJson.length() - 1) + "}";


        // Encrypting the backup.

        // Convert json backup to bytes
        byte[] backupBytes = backupJson.getBytes(StandardCharsets.UTF_8);

        // Encrypt backup
        byte[] encryptedBackupBytes = EncryptionUtil.PasswordEncryptData(backupBytes, password, RefConstants.DATA_BACKUP_NUM_HASH_ITERATIONS);

        // Construct final backup. (10 bytes file identifier + 4 bytes backupVersion + encrypted backup)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(BACKUP_FILE_IDENTIFIER.getBytes(StandardCharsets.UTF_8));
            outputStream.write(UtilFunctions.intToByteArray(backupVersion));
            outputStream.write(encryptedBackupBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Return final backup as UTF-8 string
        return outputStream.toByteArray();
    }

    public static boolean isThereAnythingToBackup() {
        return WalletConfigsManager.getInstance().hasAnyConfigs() || ContactsManager.getInstance().hasAnyContacts();
    }

    public static boolean restoreBackup(String backup, int backupVersion) {

        if (backupVersion == 0) {
            DataBackup dataBackup = new Gson().fromJson(backup, DataBackup.class);

            // restore wallets
            if (dataBackup.getWalletConfigs() != null && dataBackup.getWalletConfigs().length > 0) {
                WalletConfigsManager.getInstance().removeAllWalletConfigs();
                for (WalletConfig walletConfig : dataBackup.getWalletConfigs()) {
                    try {
                        WalletConfigsManager.getInstance().addWalletConfig(walletConfig.getAlias(), walletConfig.getType(), walletConfig.getHost(), walletConfig.getPort(), walletConfig.getCert(), walletConfig.getMacaroon());
                        WalletConfigsManager.getInstance().apply();
                    } catch (GeneralSecurityException | IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            // restore contacts
            if (dataBackup.getContacts() != null && dataBackup.getContacts().length > 0) {
                ContactsManager.getInstance().removeAllContacts();
                for (Contact contact : dataBackup.getContacts()) {
                    try {
                        ContactsManager.getInstance().addContact(contact.getContactType(), contact.getContactData(), contact.getAlias());
                        ContactsManager.getInstance().apply();
                    } catch (IOException | CertificateException | NoSuchAlgorithmException | InvalidKeyException | UnrecoverableEntryException | InvalidAlgorithmParameterException | NoSuchPaddingException | NoSuchProviderException | BadPaddingException | KeyStoreException | IllegalBlockSizeException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }
}
