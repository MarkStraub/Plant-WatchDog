package ch.mseengineering.plantwatchdog.ui.authentication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.loader.content.Loader
import ch.mseengineering.plantwatchdog.MainActivity
import ch.mseengineering.plantwatchdog.R
import ch.mseengineering.plantwatchdog.services.StoreData
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.auth.FirebaseAuthCredentialsProvider

/*
    Code based on
    https://www.youtube.com/watch?v=8I5gCLaS25w&t=1512s&ab_channel=tutorialsEU
 */

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        7
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val registerBtn: Button = findViewById(R.id.btn_register);
        val emailEdTxt: EditText = findViewById(R.id.et_register_email);
        val passwordEdTxt: EditText = findViewById(R.id.et_register_password);
        val loginTxtVew: TextView = findViewById(R.id.tv_login);

        registerBtn.setOnClickListener {
            when {
                TextUtils.isEmpty(emailEdTxt.text.toString().trim() { it <= ' ' }) -> {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Please enter email",
                        Toast.LENGTH_SHORT
                    ).show();
                }

                TextUtils.isEmpty(passwordEdTxt.text.toString().trim() { it <= ' ' }) -> {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Please enter password",
                        Toast.LENGTH_SHORT
                    ).show();
                }

                else -> {
                    val email: String = emailEdTxt.text.toString().trim() { it <= ' ' }
                    val password: String = passwordEdTxt.text.toString().trim() { it <= ' ' }

                    // Create an instance and crate a register a user with email and password
                    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(
                            OnCompleteListener<AuthResult> { task ->
                                // If the registration is successfully done
                                if (task.isSuccessful) {
                                    // Firebase registered user
                                    val firebaseUser: FirebaseUser = task.result!!.user!!

                                    Toast.makeText(
                                        this@RegisterActivity,
                                        "You are registered successfully",
                                        Toast.LENGTH_SHORT
                                    ).show();

                                    // Save the user id
                                    val storeData: StoreData = StoreData();
                                    storeData.save("userId", firebaseUser.uid);

                                    /**
                                     * Here the new user registered is automatically signed-ins so we just sign out the user
                                     * and send him to Main Screen with user id and email that user have used for registration
                                     */

                                    val intent =
                                        Intent(this@RegisterActivity, MainActivity::class.java);
                                    intent.flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK;
                                    // intent.putExtra("user_id", firebaseUser.uid);
                                    // intent.putExtra("email_id", email);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    // If the registration is not successful then show error message
                                    Toast.makeText(
                                        this@RegisterActivity,
                                        task.exception!!.message.toString(),
                                        Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        );
                }
            }
        }

        loginTxtVew.setOnClickListener {
            onBackPressed();
        }
    }
}