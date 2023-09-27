package com.yxm.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes


class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var mDriveServiceViewModel: DriveViewModel? = null
    private var mOpenFileId: String? = null

    private var mFileTitleEditText: EditText? = null
    private var mDocContentEditText: EditText? = null

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it?.data?.let {
                handleGoogleSignIn(it)
            }
        }

    private val filePickerLauncher =
        registerForActivityResult(object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return super.createIntent(context, input).apply {
                    type = "text/plain"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            }
        }) {
            it?.let {
                openFileFromFilePicker(it)
            }
        }

    private fun openFileFromFilePicker(uri: Uri) {
        Log.d(TAG, "Opening " + uri.path)
        mDriveServiceViewModel?.openFileUsingStorageAccessFramework(contentResolver, uri)
            ?.addOnSuccessListener { nameAndContent ->
                val name: String = nameAndContent.first
                val content: String = nameAndContent.second
                mFileTitleEditText!!.setText(name)
                mDocContentEditText!!.setText(content)

                // Files opened through SAF cannot be modified.
                setReadOnlyMode()
            }
            ?.addOnFailureListener { exception ->
                Log.e(
                    TAG,
                    "Unable to open file from picker.",
                    exception
                )
            }
    }

    private fun handleGoogleSignIn(resultData: Intent) {
        GoogleSignIn.getSignedInAccountFromIntent(resultData)
            .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
                Log.d(TAG, "Signed in as " + googleAccount.email)

                // Use the authenticated account to sign in to the Drive service.
                val credential = GoogleAccountCredential.usingOAuth2(
                    this, setOf(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = googleAccount.account
                val googleDriveService = Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory(),
                    credential
                ).setApplicationName("Drive API Migration")
                    .build()
                // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                // Its instantiation is required before handling any onClick actions.
                mDriveServiceViewModel = DriveViewModel(googleDriveService)
            }.addOnFailureListener { exception: Exception? ->
                Log.e(
                    TAG,
                    "Unable to sign in.",
                    exception
                )
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Store the EditText boxes to be updated when files are opened/created/modified.
        mFileTitleEditText = findViewById(R.id.file_title_edittext)
        mDocContentEditText = findViewById(R.id.doc_content_edittext)

        // Set the onClick listeners for the button bar.

        // Set the onClick listeners for the button bar.
        findViewById<View>(R.id.open_btn).setOnClickListener {
            openFilePicker()
        }
        findViewById<View>(R.id.create_btn).setOnClickListener {
            createFile()
        }
        findViewById<View>(R.id.save_btn).setOnClickListener {
            saveFile()
        }
        findViewById<View>(R.id.query_btn).setOnClickListener {
            query()
        }

        // Authenticate the user. For most apps, this should be done when the user performs an
        // action that requires Drive access rather than in onCreate.
        requestSignIn()
    }

    private fun saveFile() {
        if (mDriveServiceViewModel != null && mOpenFileId != null) {
            Log.d(TAG, "Saving $mOpenFileId")
            val fileName = mFileTitleEditText!!.text.toString()
            val fileContent = mDocContentEditText!!.text.toString()
            mDriveServiceViewModel?.saveFile(mOpenFileId!!, fileName, fileContent)
                ?.addOnFailureListener { exception ->
                    Log.e(
                        TAG,
                        "Unable to save file via REST.",
                        exception
                    )
                }
        }
    }

    /**
     * Updates the UI to read-only mode.
     */
    private fun setReadOnlyMode() {
        mFileTitleEditText!!.isEnabled = false
        mDocContentEditText!!.isEnabled = false
        mOpenFileId = null
    }

    /**
     * Updates the UI to read/write mode on the document identified by `fileId`.
     */
    private fun setReadWriteMode(fileId: String) {
        mFileTitleEditText!!.isEnabled = true
        mDocContentEditText!!.isEnabled = true
        mOpenFileId = fileId
    }

    /**
     * Creates a new file via the Drive REST API.
     */
    private fun createFile() {
        Log.d(TAG, "Creating a file.")
        mDriveServiceViewModel?.createFile()
            ?.addOnSuccessListener { fileId -> readFile(fileId) }
            ?.addOnFailureListener { exception ->
                Log.e(
                    TAG,
                    "Couldn't create file.",
                    exception
                )
            }
    }

    private fun readFile(fileId: String) {
        Log.d(TAG, "Reading file " + fileId)
        mDriveServiceViewModel?.readFile(fileId)
            ?.addOnSuccessListener { nameAndContent ->
                val name = nameAndContent.first
                val content = nameAndContent.second

                mFileTitleEditText?.setText(name)
                mDocContentEditText?.setText(content)
                setReadWriteMode(fileId)
            }?.addOnFailureListener { exception ->
                Log.e(TAG, "Couldn't read file.", exception)
            }

    }

    private fun query() {
        Log.d(TAG, "Querying for files.")
        mDriveServiceViewModel?.queryFiles()
            ?.addOnSuccessListener { fileList ->
                val builder = StringBuilder()
                for (file in fileList?.files ?: mutableListOf()) {
                    builder.append(file.name).append("\n")
                }
                val fileNames = builder.toString()
                mFileTitleEditText!!.setText("File List")
                mDocContentEditText!!.setText(fileNames)
                setReadOnlyMode()
            }?.addOnFailureListener { exception ->
                Log.e(
                    TAG,
                    "Unable to query files.",
                    exception
                )
            }
    }

    private fun openFilePicker() {
        mDriveServiceViewModel?.let {
            filePickerLauncher.launch(arrayOf("text"))
        }
    }

    /**
     * Starts a sign-in activity using [.REQUEST_CODE_SIGN_IN].
     */
    private fun requestSignIn() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(this, signInOptions)

        // The result of the sign-in Intent is handled in onActivityResult.
        googleSignInLauncher.launch(client.signInIntent)
    }
}