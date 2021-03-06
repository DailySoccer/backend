package controllers;

import actions.AllowCors;
import actions.UserAuthenticated;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.paypal.api.payments.Payment;
import com.paypal.api.payments.PaymentHistory;
import com.paypal.base.rest.PayPalRESTException;
import model.*;
import model.jobs.CompleteOrderJob;
import model.paypal.PaypalIPNMessage;
import model.paypal.PaypalPayment;
import model.shop.Catalog;
import model.shop.Order;
import model.shop.Product;
import model.shop.ProductMoney;
import org.bson.types.ObjectId;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import play.Logger;
import play.data.Form;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import utils.ReturnHelper;

import java.util.Map;

@AllowCors.Origin
public class PaypalController extends Controller {
    static final String SELLER_EMAIL = "epiceleven@business.com";

    // Las rutas relativas al CLIENT a las que enviaremos las respuestas proporcionadas por Paypal
    static final String CLIENT_CANCEL_PATH = "#/shop/response/canceled";
    static final String CLIENT_SUCCESS_PATH = "#/shop/response/success";

    // Las keys que incluimos en las urls de respuesta de Paypal
    static final String QUERY_STRING_SUCCESS_KEY = "success";
    static final String QUERY_STRING_CANCEL_KEY = "cancel";
    static final String QUERY_STRING_ORDER_KEY = "orderId";

    // Los distintos estados posibles de un pago
    static final String PAYMENT_STATE_CREATED = "created";
    static final String PAYMENT_STATE_APPROVED = "approved";
    static final String PAYMENT_STATE_FAILED = "failed";
    static final String PAYMENT_STATE_PENDING = "pending";
    static final String PAYMENT_STATE_CANCELED = "canceled";
    static final String PAYMENT_STATE_EXPIRED = "expired";

    static final String EVENT_PAYMENT_SALE_COMPLETED = "PAYMENT.SALE.COMPLETED";
    static final String EVENT_PAYMENT_SALE_REVERSED = "PAYMENT.SALE.REVERSED";

    public static Result approvalPayment(String userId, Double amount) {
        // Obtenemos desde qué url están haciendo la solicitud
        String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : Order.REFERER_URL_DEFAULT;

        // Si Paypal no responde con un adecuado "approval url", cancelaremos la solicitud
        Result result = null;

        try {
            // Especificar a qué host enviaremos los urls de respuesta
            PaypalPayment.instance().setHostName(request().host());

            // Crear el identificador del nuevo pedido
            ObjectId orderId = new ObjectId();

            // Producto que quiere comprar
            ProductMoney product = new ProductMoney("Payment", Money.of(CurrencyUnit.EUR, amount), "Product", "", Money.of(CurrencyUnit.EUR, amount));

            // Creamos la solicitud de pago (le proporcionamos el identificador del pedido para referencias posteriores)
            Payment payment = PaypalPayment.instance().createPayment(orderId, product);
            Model.paypalResponses().insert(payment.toJSON());

            // Creamos el pedido (con el identificador generado y el de la solicitud de pago)
            //      Únicamente almacenamos el referer si no es el de "por defecto"
            Order.create(
                    orderId,
                    new ObjectId(userId),
                    Order.TransactionType.PAYPAL,
                    payment.getId(),
                    ImmutableList.of(product),
                    refererUrl);

            String redirectUrl = PaypalPayment.instance().getApprovalURL(payment);
            if (redirectUrl != null) {
                result = redirect(redirectUrl);
            }
        } catch (PayPalRESTException e) {
            Logger.error("WTF 7741: ", e);
            result = null;
        }

        if (result == null) {
            Logger.error("WTF 1209: Paypal: Link approval not found");
            result = redirect(refererUrl + CLIENT_CANCEL_PATH);
        }
        return result;
    }

    public static Result approvalBuy(String userId, String productId) {
        // Obtenemos desde qué url están haciendo la solicitud
        String refererUrl = request().hasHeader("Referer") ? request().getHeader("Referer") : Order.REFERER_URL_DEFAULT;

        // Si Paypal no responde con un adecuado "approval url", cancelaremos la solicitud
        Result result = null;

        try {
            // Especificar a qué host enviaremos los urls de respuesta
            PaypalPayment.instance().setHostName(request().host());

            // Crear el identificador del nuevo pedido
            ObjectId orderId = new ObjectId();

            // Producto que quiere comprar
            ProductMoney product = (ProductMoney) Catalog.findOne(productId);

            // Creamos la solicitud de pago (le proporcionamos el identificador del pedido para referencias posteriores)
            Payment payment = PaypalPayment.instance().createPayment(orderId, product);
            Model.paypalResponses().insert(payment.toJSON());

            // Creamos el pedido (con el identificador generado y el de la solicitud de pago)
            //      Únicamente almacenamos el referer si no es el de "por defecto"
            Order.create(
                    orderId,
                    new ObjectId(userId),
                    Order.TransactionType.PAYPAL,
                    payment.getId(),
                    ImmutableList.of(product),
                    refererUrl);

            String redirectUrl = PaypalPayment.instance().getApprovalURL(payment);
            if (redirectUrl != null) {
                result = redirect(redirectUrl);
            }
        } catch (PayPalRESTException e) {
            Logger.error("WTF 7741: ", e);
            result = null;
        }

        if (result == null) {
            Logger.error("WTF 1209: Paypal: Link approval not found");
            result = redirect(refererUrl + CLIENT_CANCEL_PATH);
        }
        return result;
    }

    /**
     * Recibiremos la respuesta de Paypal. Si es "success" procederemos a "payment.execute"
     *  ?success=true&paymentId=PAY-9AB311545W6759534KRNXYOA&token=EC-9NA34841MA1300346&PayerID=WZADK9MZSSL5N
     *  ?cancel=true
     */
    public static Result executePayment() {
        final String PAYMENT_ID = "paymentId";
        final String PAYER_ID = "PayerID";

        // Identificador del pedido que hemos incluido en las urls de respuesta de Paypal
        String orderId = request().getQueryString(QUERY_STRING_ORDER_KEY);

        // Buscamos el pedido mediante su identificador
        Order order = Order.findOne(orderId);

        // Respuesta "exitosa"?
        boolean success = request().queryString().containsKey(QUERY_STRING_SUCCESS_KEY);
        if (success) {
            // Identificador del pago
            String paymentId = request().getQueryString(PAYMENT_ID);
            if (!order.paymentId.equals(paymentId)) {
                Logger.error("WTF 7743: order.paymentId({}) != paymentId({})", order.paymentId, paymentId);
            }

            // Obtener el identificador del "pagador" (Paypal lo proporciona en la url)
            String payerId = request().getQueryString(PAYER_ID);

            order.setWaitingPayment(payerId);

            try {
                // Completar el pago "ya aprobado" por el pagador
                Payment payment = PaypalPayment.instance().completePayment(paymentId, payerId);
                Model.paypalResponses().insert(payment.toJSON());

                // Evaluar la respuesta de Paypal (values: "created", "approved", "failed", "canceled", "expired", "pending")
                if (payment.getState().equalsIgnoreCase(PAYMENT_STATE_APPROVED)) {
                    // Pago aprobado
                    CompleteOrderJob.create(order.orderId);
                    // order.setCompleted();
                }
                else if (payment.getState().equalsIgnoreCase(PAYMENT_STATE_PENDING)) {
                    // El pago permanece pendiente de evaluación posterior (ipnListener)
                    order.setPending();
                }
                else{
                    // Pago cancelado
                    order.setCanceled();
                    success = false;
                }
            } catch (PayPalRESTException e) {
                Logger.error("WTF 7742: ", e);

                // Pago cancelado
                order.setCanceled();
                success = false;
            }
        }
        else {
            // Respuesta "cancelada"?
            boolean canceled = request().queryString().containsKey(QUERY_STRING_CANCEL_KEY);
            if (canceled) {
                order.setCanceled();
            }
        }

        String refererUrl = order.referer != null ? order.referer : Order.REFERER_URL_DEFAULT;
        return redirect(refererUrl + (success ? CLIENT_SUCCESS_PATH : CLIENT_CANCEL_PATH));
    }

    @UserAuthenticated
    public static Result withdrawFunds(int amount) {
        User theUser = (User) ctx().args.get("User");

        Refund refund = new Refund(theUser.userId, Money.of(CurrencyUnit.EUR, amount));
        refund.insert();

        return new ReturnHelper(ImmutableMap.of(
                "result", "ok"
        )).toResult();
    }

    public static Result verifyPayment(String paymentId) {
        String response = "";

        try {
            Payment payment = PaypalPayment.instance().verifyPayment(paymentId);
            response = payment.toJSON();

        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }

        return ok(response);
    }

    public static Result history() {
        String response = "";

        try {
            PaymentHistory paymentHistory = PaypalPayment.instance().history();
            response = paymentHistory.toJSON();

        } catch (PayPalRESTException e) {
            e.printStackTrace();
        }

        return ok(response);
    }

    public static Result ipnListener() {
        Map<String, String> data = Form.form().bindFromRequest().data();
        Logger.info("IPN: {}", data.toString());

        PaypalIPNMessage ipnMessage = new PaypalIPNMessage(data, PaypalPayment.instance().getSdkConfig());
        if (ipnMessage.validate()) {
            String receiverEmail = ipnMessage.getIpnValue(PaypalIPNMessage.FIELD_RECEIVER_EMAIL);
            if (receiverEmail.equalsIgnoreCase(SELLER_EMAIL)) {
                // TODO: ¿Queremos verificar el email?
            }
            Logger.info("receiverEmail: {}", ipnMessage.getIpnValue(PaypalIPNMessage.FIELD_RECEIVER_EMAIL));

            // TODO: Verificar el importe del pedido
            // TODO: Verificar estado del pedido vs estado del IPNMessage
            if (ipnMessage.isPaymentStatusCompleted()) {
                // Actualizaremos el pedido únicamente si está "pending" (esperando respuesta)
                //  de esta forma garantizamos que no tenemos en cuenta mensajes antiguos o repetidos
                String fieldCustomId = ipnMessage.getIpnValue(PaypalIPNMessage.FIELD_CUSTOM_ID);
                Order order = Order.findOne(fieldCustomId);
                // Aseguramos que siempre completamos el pedido, la operación "setCompleted" es idempotente
                if (order != null) {
                    CompleteOrderJob.create(order.orderId);
                    Logger.info("IPN Order Valid: {}", order.orderId.toString());
                    // order.setCompleted();
                }
                else {
                    Logger.warn("IPN Order Unknown: {}", fieldCustomId);
                }
            }

            String transactionId = ipnMessage.getIpnValue(PaypalIPNMessage.FIELD_TRANSACTION_ID);
            String paymentStatus = ipnMessage.getIpnValue(PaypalIPNMessage.FIELD_PAYMENT_STATUS);
            Model.paypalResponses()
                    .update("{txn_id: #, payment_status: #}", transactionId, paymentStatus)
                    .upsert()
                    .with(ipnMessage.getIpnMap());

            Logger.info("IPN Message valid");
        }
        else {
            Logger.error("IPN Message invalid: {}", data.toString());
        }
        return ok();
    }

    @BodyParser.Of(BodyParser.Json.class)
    public static Result webhook() {
        JsonNode json = request().body().asJson();
        Model.paypalResponses().insert(json);
        Logger.info("webhook: {}", json);

        if (json.get("resource_type").textValue().equalsIgnoreCase("sale")) {
            JsonNode jsonResource = json.findPath("resource");
            if (json.get("event_type").textValue().equalsIgnoreCase(EVENT_PAYMENT_SALE_COMPLETED)) {
                String paymentId = jsonResource.get("parent_payment").textValue();
                String state = jsonResource.get("state").textValue(); // pending; completed; refunded; partially_refunded
                Logger.info("paymentId: {} - state: {}", paymentId, state);
            } else if (json.get("event_type").textValue().equalsIgnoreCase(EVENT_PAYMENT_SALE_REVERSED)) {
                String paymentId = jsonResource.get("parent_payment").textValue();
                String state = jsonResource.get("state").textValue(); // pending; completed; refunded; partially_refunded
                String pendingReason = jsonResource.get("pending_reason").textValue();
                String reasonCode = jsonResource.get("reason_code").textValue();
                Logger.info("paymentId: {} - state: {} - pendingReason: {} - reasonCode: {}",
                        paymentId, state, pendingReason, reasonCode);
            }
        }
        return ok();
    }
}
