(ns puppetlabs.services.master.master-service
  (:require [clojure.tools.logging :as log]
            [compojure.core :as compojure]
            [puppetlabs.trapperkeeper.core :refer [defservice]]
            [puppetlabs.services.master.master-core :as core]
            [puppetlabs.puppetserver.certificate-authority :as ca]
            [puppetlabs.trapperkeeper.services :as tk-services]))

(defservice master-service
  [[:WebroutingService add-ring-handler get-route]
   [:PuppetServerConfigService get-config]
   [:RequestHandlerService handle-request]
   [:CaService initialize-master-ssl! retrieve-ca-cert! retrieve-ca-crl!]
   [:JRubyPuppetService]]
  (init
   [this context]
   (core/validate-memory-requirements!)
   (let [path        (get-route this :master-routes)
         config      (get-config)
         certname    (get-in config [:puppet-server :certname])
         localcacert (get-in config [:puppet-server :localcacert])
         hostcrl     (get-in config [:puppet-server :hostcrl])
         settings    (ca/config->master-settings config)
         jruby-service (tk-services/get-service this :JRubyPuppetService)]

     (retrieve-ca-cert! localcacert)
     (retrieve-ca-crl! hostcrl)
     (initialize-master-ssl! settings certname)

     (log/info "Master Service adding ring handlers")
     (add-ring-handler
       this
      (compojure/context path [] (core/build-ring-handler handle-request))
      {:route-id :master-routes})
     (add-ring-handler this (core/construct-invalid-request-handler jruby-service) {:route-id :invalid-in-puppet-4}))
   context)
  (start
    [this context]
    (log/info "Puppet Server has successfully started and is now ready to handle requests")
    context))
