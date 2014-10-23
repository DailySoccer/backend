package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.mongodb.MongoException;
import com.stormpath.sdk.account.Account;
import model.GlobalDate;
import model.Model;
import model.Session;
import model.User;
import model.stormpath.StormPathClient;
import play.Logger;
import play.Play;
import play.data.Form;
import play.data.validation.Constraints.Email;
import play.data.validation.Constraints.MinLength;
import play.data.validation.Constraints.Required;
import play.libs.Crypto;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.HashMap;
import java.util.Map;

import static play.data.Form.form;

@AllowCors.Origin
public class LoginController extends Controller {

    // https://github.com/playframework/playframework/tree/master/samples/java/forms
    public static class SignupParams {

        public String firstName;

        public String lastName;

        @Required @MinLength(value = 4)
        public String nickName;

        @Required @Email public String email;

        @Required public String password;
    }


    public static class LoginParams {
        @Required public String email;
        @Required public String password;
    }

    public static class AskForPasswordResetParams {
        @Required public String email;
    }

    public static class VerifyPasswordResetTokenParams {
        @Required public String token;
    }

    public static class PasswordResetParams {
        @Required public String password;
        @Required public String token;
    }

    public static Result askForPasswordReset() {

        Form<AskForPasswordResetParams> askForPasswordResetParamsForm = form(AskForPasswordResetParams.class).bindFromRequest();
        AskForPasswordResetParams params = null;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!askForPasswordResetParamsForm.hasErrors()) {
            params = askForPasswordResetParamsForm.get();

            String askForPasswordResetErrors = StormPathClient.instance().askForPasswordReset(params.email);

            if (askForPasswordResetErrors == null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password reset sent"));
            }
            else {
                returnHelper.setKO(ImmutableMap.of("error", askForPasswordResetErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result verifyPasswordResetToken() {
        Form<VerifyPasswordResetTokenParams> verifyPasswordResetTokenParamsForm = form(VerifyPasswordResetTokenParams.class).bindFromRequest();
        VerifyPasswordResetTokenParams params = null;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!verifyPasswordResetTokenParamsForm.hasErrors()) {
            params = verifyPasswordResetTokenParamsForm.get();

            String verifyPasswordResetTokenErrors = StormPathClient.instance().verifyPasswordResetToken(params.token);

            if (verifyPasswordResetTokenErrors == null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password reset token valid"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", verifyPasswordResetTokenErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result resetPasswordWithToken() {

        Form<PasswordResetParams> passwordResetParamsForm = form(PasswordResetParams.class).bindFromRequest();
        PasswordResetParams params = null;

        ReturnHelper returnHelper = new ReturnHelper();

        if (!passwordResetParamsForm.hasErrors()) {
            params = passwordResetParamsForm.get();

            String resetPasswordWithTokenErrors = StormPathClient.instance().resetPasswordWithToken(params.token, params.password);

            if (resetPasswordWithTokenErrors == null) {
                returnHelper.setOK(ImmutableMap.of("success", "Password resetted successfully"));
            } else {
                returnHelper.setKO(ImmutableMap.of("error", resetPasswordWithTokenErrors));
            }
        }
        return returnHelper.toResult();
    }


    public static Result signup() {
        Form<SignupParams> signupForm = form(SignupParams.class).bindFromRequest();

        JsonNode result = signupForm.errorsAsJson();

        if (!signupForm.hasErrors()) {
            SignupParams params = signupForm.get();
            Map<String, String> createUserErrors = createUser(params);

            if (createUserErrors != null && !createUserErrors.isEmpty()) {
                for (String key : createUserErrors.keySet()) {
                    signupForm.reject(key, createUserErrors.get(key));
                }
                result = signupForm.errorsAsJson();
            }
            else {
                result = new ObjectMapper().createObjectNode().put("result", "ok");
            }
        }

        return new ReturnHelper(!signupForm.hasErrors(), result).toResult();
    }


    private static Map<String, String> createUser(SignupParams theParams) {

        StormPathClient stormPathClient = new StormPathClient();
        String registerError = stormPathClient.register(theParams.nickName, theParams.email, theParams.password);

        if (registerError == null) {
            // Puede ocurrir que salte una excepcion por duplicidad. No seria un error de programacion puesto que, aunque
            // comprobamos si el email o nickname estan duplicados antes de llamar aqui, es posible que se creen en
            // paralelo. Por esto, la vamos a controlar explicitamente
            try {
                Model.users().insert(new User(theParams.firstName, theParams.lastName, theParams.nickName,
                                              theParams.email, theParams.password));
            } catch (MongoException exc) {
                Logger.error("createUser: ", exc);
                HashMap mongoError = new HashMap<String, String>();
                mongoError.put("email", "Hubo un problema en la creación de tu usuario");
                return mongoError;
            }

        }
        return translateError(registerError);
    }

    private static Map<String, String> translateError(String error) {
        HashMap returnError = new HashMap<String, String>();
        if (error == null) {
            return returnError;
        }
        if (error.contains("Account with that email already exists.  Please choose another email.")) {
            returnError.put("email", "Ya existe una cuenta con ese email. Indica otro email.");
        }
        else
        if (error.contains("Account with that username already exists.  Please choose another username.")) {
            returnError.put("nickName", "Ya existe una cuenta con ese nombre de usuario. Escoge otro.");
        }
        else
        if (error.contains("Account password minimum length not satisfied.")) {
            returnError.put("password", "La contraseña es demasiado corta.");
        }
        else
        if (error.contains("Password requires a lowercase character!")) {
            returnError.put("password", "La contraseña debe contener al menos una letra minúscula");
        }
        else
        if (error.contains("Password requires an uppercase character!")) {
            returnError.put("password", "La contraseña debe contener al menos una letra mayúscula");
        }
        else
        if (error.contains("Password requires a numeric character!")) {
            returnError.put("password", "La contraseña debe contener al menos un número");
        }

        //TODO: Incluir:
        /*
         Cannot invoke the action, eventually got an error: com.stormpath.sdk.resource.ResourceException: HTTP 409, Stormpath 409 (mailto:support@stormpath.com): Another resource with that information already exists. This is likely due to a constraint violation. Please update to retrieve the latest version of the information and try again if necessary.
         */

        else {
            Logger.error("Error no traducido: \n {}", error);
        }
        return returnError;
    }


    public static Result login() {

        Form<LoginParams> loginParamsForm = Form.form(LoginParams.class).bindFromRequest();
        ReturnHelper returnHelper = new ReturnHelper();

        if (!loginParamsForm.hasErrors()) {
            LoginParams loginParams = loginParamsForm.get();

            boolean isTest = loginParams.email.endsWith("@test.com");

            // Si no es Test, entramos a través de Stormpath
            Account account = isTest? null : StormPathClient.instance().login(loginParams.email, loginParams.password);

            // Si no entra correctamente
            if (account == null && !isTest) {
                loginParamsForm.reject("email", "email or password incorrect");
                returnHelper.setKO(loginParamsForm.errorsAsJson());
            }
            // Si entramos correctamente
            else {
                // Buscamos el usuario en Mongo
                User theUser = Model.users().findOne("{email:'#'}", loginParams.email).as(User.class);

                // Si el usuario tiene cuenta en StormPath, pero no existe en nuestra BD, lo creamos en nuestra BD
                if (theUser == null && account != null) {
                    Logger.debug("Creamos el usuario porque no esta en nuestra DB y sí en Stormpath: {}", account.getEmail());
                    Model.users().insert(new User(account.getGivenName(), account.getSurname(),
                                                  account.getUsername(), account.getEmail(), ""));
                }

                if (Play.isDev()) {
                    Logger.info("Estamos en desarrollo: El email {} sera el sessionToken y pondremos una cookie", theUser.email);

                    // Durante el desarrollo en local usamos cookies y el email como sessionToken para que sea mas facil debugear
                    // por ejemplo usando Postman
                    response().setCookie("sessionToken", theUser.email);

                    returnHelper.setOK(new Session(theUser.email, theUser.userId, GlobalDate.getCurrentDate()));
                }
                else {
                    // En produccion NO mandamos cookie. Esto evita CSRFs. Esperamos que el cliente nos mande el sessionToken
                    // cada vez como parametro en una custom header.
                    String sessionToken = Crypto.generateSignedToken();
                    Session newSession = new Session(sessionToken, theUser.userId, GlobalDate.getCurrentDate());
                    Model.sessions().insert(newSession);

                    returnHelper.setOK(newSession);
                }
            }
        }

        return returnHelper.toResult();
    }

    @UserAuthenticated
    public static Result getUserProfile() {
        return new ReturnHelper(ctx().args.get("User")).toResult();
    }

    public static class ChangeParams {

        public String firstName;
        public String lastName;
        public String nickName;
        @Email public String email;

        public String password;
    }

    @UserAuthenticated
    public static Result changeUserProfile() {

        User theUser = (User)ctx().args.get("User");

        Form<ChangeParams> changeParamsForm = form(ChangeParams.class).bindFromRequest();
        ChangeParams params;
        JsonNode result = new ObjectMapper().createObjectNode().put("result", "ok");

        boolean somethingChanged = false;

        if (!changeParamsForm.hasErrors()) {
            params = changeParamsForm.get();
            String originalEmail = theUser.email;

            if (!params.firstName.isEmpty()) {
                theUser.firstName = params.firstName;
                somethingChanged = true;
            }
            if (!params.lastName.isEmpty()) {
                theUser.lastName = params.lastName;
                somethingChanged = true;
            }

            if (!params.email.isEmpty()) {
                theUser.email = params.email;
                somethingChanged = true;
            }

            Map<String, String> allErrors = changeStormpathProfile(theUser, params, somethingChanged, originalEmail);

            if (allErrors.isEmpty()) {
                Model.users().update(theUser.userId).with(theUser);
            }
            else {
                for (String key : allErrors.keySet()) {
                    changeParamsForm.reject(key, allErrors.get(key));
                }
                result = changeParamsForm.errorsAsJson();
            }

        }
        else {
            result = changeParamsForm.errorsAsJson();
        }

        return new ReturnHelper(!changeParamsForm.hasErrors(), result).toResult();
    }


    private static Map<String, String> changeStormpathProfile(User theUser, ChangeParams params, boolean somethingChanged, String originalEmail) {
        StormPathClient stormPathClient = new StormPathClient();

        Map<String, String> allErrors = new HashMap<String,String>();

        if (!originalEmail.endsWith("test.com")) {
            if (somethingChanged) {
                Map changeUserProfileErrors =  translateError(stormPathClient.changeUserProfile(originalEmail, theUser.firstName, theUser.lastName,
                        theUser.email));
                allErrors.putAll(changeUserProfileErrors);
            }

            if (params.password.length() > 0) {
                Map updatePasswordErrors =  translateError(stormPathClient.updatePassword(originalEmail, params.password));
                allErrors.putAll(updatePasswordErrors);
            }
        }
        return allErrors;
    }


}