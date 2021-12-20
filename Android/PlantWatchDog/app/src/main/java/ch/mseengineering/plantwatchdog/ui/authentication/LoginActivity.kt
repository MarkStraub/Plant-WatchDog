package ch.mseengineering.plantwatchdog.ui.authentication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import ch.mseengineering.plantwatchdog.MainActivity
import ch.mseengineering.plantwatchdog.R
import ch.mseengineering.plantwatchdog.services.StoreData
import com.google.firebase.auth.FirebaseAuth

/*
    Code based on
    https://www.youtube.com/watch?v=8I5gCLaS25w&t=1512s&ab_channel=tutorialsEU
 */

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Check if the user is already signed in
        val storeData: StoreData = StoreData();
        if (storeData.readString("userId") !== null) {
            val intent =
                Intent(this@LoginActivity, MainActivity::class.java);
            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK;
            startActivity(intent);
            finish();
        }

        // Otherwise show Login screen
        val loginBtn: Button = findViewById(R.id.btn_login);
        val emailEdTxt: EditText = findViewById(R.id.et_login_email);
        val passwordEdTxt: EditText = findViewById(R.id.et_login_password);
        val registerTxtVew: TextView = findViewById(R.id.tv_register);

        loginBtn.setOnClickListener {
            when {
                TextUtils.isEmpty(emailEdTxt.text.toString().trim() { it <= ' ' }) -> {
                    Toast.makeText(
                        this@LoginActivity,
                        "Please enter email",
                        Toast.LENGTH_SHORT
                    ).show();
                }

                TextUtils.isEmpty(passwordEdTxt.text.toString().trim() { it <= ' ' }) -> {
                    Toast.makeText(
                        this@LoginActivity,
                        "Please enter password",
                        Toast.LENGTH_SHORT
                    ).show();
                }

                else -> {
                    val email: String = emailEdTxt.text.toString().trim() { it <= ' ' }
                    val password: String = passwordEdTxt.text.toString().trim() { it <= ' ' }

                    // Log in using FirebaseAuth
                    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            // If the login is successfully done
                            if (task.isSuccessful) {

                                Toast.makeText(
                                    this@LoginActivity,
                                    "You are logged in successfully",
                                    Toast.LENGTH_SHORT
                                ).show();

                                // Save the user id
                                val storeData: StoreData = StoreData();
                                storeData.save(
                                    "userId",
                                    FirebaseAuth.getInstance().currentUser!!.uid
                                );

                                /**
                                 * Here the is signed-in so we send him to Main Screen
                                 */

                                val intent =
                                    Intent(this@LoginActivity, MainActivity::class.java);
                                intent.flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK;
                                startActivity(intent);
                                finish();
                            } else {
                                // If the registration is not successful then show error message
                                Toast.makeText(
                                    this@LoginActivity,
                                    task.exception!!.message.toString(),
                                    Toast.LENGTH_SHORT
                                ).show();
                            }
                        };
                }
            }
        }

        registerTxtVew.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java));
        }
    }
}