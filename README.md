# Secure IPC Messenger Sample

This project demonstrates **secure App-to-App IPC** on Android using:

- **Messenger IPC** for async cross-process communication.
- **RSA public/private key exchange** for hybrid encryption.
- **AES session key** for encrypting user data.
- **Foreground Service** for modern Android restrictions.
- **Multi-module structure**: `comm` (Client app), `service_app` (Service app), `shared` (common crypto).

---

## **Modules**
`:shared`     | Contains `CryptoHelper` — all crypto code.
`:service_app` | Runs the secure `SecureService` with RSA & AES logic.
`:comm`       | The client app. Binds to `SecureService`, exchanges keys, sends secure text.

---

## **Security flow**

1️ Client binds to `SecureService`.  
2️ Client requests **RSA Public Key**.  
3️ Client generates a **random AES session key**, encrypts it with RSA, sends to Service.  
4️ Client encrypts user text with AES, sends it securely.  
5 Service decrypts, logs, responds with AES-encrypted reply.  
6️ Client decrypts and displays reply.

---

## **Client Compose UI**

- `LazyColumn` shows all received decrypted replies.
- `TextField` and `Button` pinned to bottom.
- Uses `StateFlow` for idiomatic Compose reactivity.

---

## ✅ **How to run**

### 1️ **Build both apps**
- `:comm` — the **Client**
- `:service_app` — the **Service**

You can run them as separate configurations or build APKs.

---

### 2️ **Install and launch the Service app first**

- Open **`service_app`**.
- Launch its MainActivity → this starts the Foreground Service.
- Keep it running in background (Android requires foreground to bind across apps).

---

### 3️ **Launch the Client app**

- Open **`IPC comm`**.
- Enter text and press **Send Secure Message**.
- Observe logs and the decrypted responses list.

---

## **Key files**
`CryptoHelper.kt` | RSA + AES encryption and decryption.
`SecureService.kt` | Foreground Service that handles Messenger IPC securely.
`SecureIpcViewModel.kt` | Client ViewModel with `StateFlow` for Compose.
`MainActivity.kt` | Compose UI: input field, button, and list of replies.

---

##  **Important notes**

Uses `ForegroundService` to meet modern background restrictions.   
Uses `CoroutineScope` for heavy crypto off the UI thread.  

---

## **Build & Test**
