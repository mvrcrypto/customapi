(ns components.login
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r]
            [secretary.core :refer [dispatch!]]
            [app.state :refer [app-state]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(defn ^:private login
  "Log a user with AWS credentials"
  [access-key secret-access-key]
  (go (let [creds {:access-key @access-key
                   :secret-key @secret-access-key}
            response (<! (http/post "/api/login" {:form-params creds}))]
    (if (:success response)
      (swap! app-state assoc :creds creds
                             :projects (get-in response [:body :projects])
                             :page :home)
      (reset! secret-access-key "")))))

(defn ^:private login-form []
  (let [region-list (r/atom {})
        access-key (r/atom "")
        secret-key (r/atom "")
        get-regions (fn [] (go (swap! region-list assoc :list
                      (get-in (<! (http/get "/api/regions")) [:body :data]))))]
  (get-regions)
  (fn []
    [:div {:class "panel panel-primary"}
      [:div {:class "panel-heading"} "Please Login"]
      [:div {:class "panel-body"}
        [:form {:class "form-horizontal form-group col-sm-12"}
          [:div {:class "form-group"}
            [:input {:type "text" :id "inputAccessKey"
                     :class "form-control" :placeholder "Access key"
                     :on-change #(reset! access-key (-> % .-target .-value))
                     :required "" :value @access-key}]]
          [:div {:class "form-group"}
            [:input {:type "password" :id "inputSecretKey"
                     :class "form-control" :placeholder "Secret key"
                     :on-change #(reset! secret-key (-> % .-target .-value ))
                     :required "" :value @secret-key}]]
          [:div {:class "form-group"}
            [:select {:multiple "" :class "form-control" :id "inputRegion"}
              (for [reg (:list @region-list)] ^{:key reg}
                [:option {:value (:value reg)} (:name reg)])]]
          [:div {:class "form-group"}
            [:label [:input {:id "inputRemember" :type "checkbox"
                             :defaultChecked true}]
              " Remember me"]]
          [:div {:class "form-group"}
            [:button {:on-click #(login access-key secret-key)
                      :class "btn btn-success btn-block" :type "submit"}
              "Login"]]]]])))

(defn component []
  [:div [:h1 "Login Page"]
    [login-form]])
