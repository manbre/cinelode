import {WebView} from '@amazon-devices/webview';
import {
  useHideSplashScreenCallback,
  usePreventHideSplashScreen,
  Linking,
} from '@amazon-devices/react-native-kepler';
import * as React from 'react';
import {useEffect, useRef, useState} from 'react';
import {
  BackHandler,
  Image,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import config from '../config.json';
import {isValidFrontendUrl} from './config';

export const App = () => {
  const webRef = useRef<React.ElementRef<typeof WebView>>(null);
  const webHistoryCanGoBack = useRef(false);
  const webPlayerIsFullscreen = useRef(false);
  const loadFailed = useRef(false);
  const webViewUrl = useRef(
    `${config.frontendUrl}?cinelodeLaunch=${Date.now()}&cinelodeNativeLoader=1`,
  ).current;
  const currentWebViewUrl = useRef(webViewUrl);
  const validUrl = isValidFrontendUrl(config.frontendUrl);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>(
    validUrl ? 'loading' : 'error',
  );
  const [message, setMessage] = useState(
    validUrl ? '' : 'Eine gültige Frontend-URL muss konfiguriert werden.',
  );
  const [attempt, setAttempt] = useState(0);

  usePreventHideSplashScreen();
  const hideSplashScreen = useHideSplashScreenCallback();

  useEffect(() => {
    if (!validUrl) {
      console.error('Cinelode: frontend URL is missing or invalid');
      hideSplashScreen();
    }
  }, [hideSplashScreen, validUrl]);

  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (webPlayerIsFullscreen.current && webRef.current) {
        webRef.current.injectJavaScript(
          "document.dispatchEvent(new KeyboardEvent('keydown', {key: 'BrowserBack', bubbles: true})); true;",
        );
        return true;
      }
      if (webHistoryCanGoBack.current && webRef.current) {
        webRef.current.goBack();
        return true;
      }
      webRef.current?.injectJavaScript(
        "document.dispatchEvent(new KeyboardEvent('keydown', {key: 'BrowserBack', bubbles: true})); true;",
      );
      return true;
    });
    return () => subscription.remove();
  }, []);

  const fail = (reason: string) => {
    loadFailed.current = true;
    console.error(`Cinelode: ${reason}`);
    setMessage(reason);
    setStatus('error');
    hideSplashScreen();
  };

  const isMainDocumentUrl = (url: string) => {
    if (!url) {
      return true;
    }

    const frontend = config.frontendUrl.replace(/\/+$/, '');
    const failed = url.split(/[?#]/, 1)[0].replace(/\/+$/, '');
    return failed === frontend;
  };

  const isFrontendPage = (url: string) => {
    const frontend = config.frontendUrl.replace(/\/+$/, '');
    return url === frontend || ['/', '?', '#'].some(
      separator => url.startsWith(frontend + separator),
    );
  };

  const externalOpenFailed = () => {
    console.error('Cinelode: external link could not be opened');
    webRef.current?.injectJavaScript(
      "window.dispatchEvent(new Event('cinelode-external-open-error')); true;",
    );
  };

  return (
    <View style={styles.container}>
      {validUrl && (
        <WebView
          key={attempt}
          ref={webRef}
          testID="cinelode-webview"
          style={styles.webview}
          hasTVPreferredFocus
          javaScriptEnabled
          domStorageEnabled
          allowsDefaultMediaControl
          mediaPlaybackRequiresUserAction={false}
          mixedContentMode="never"
          source={{uri: webViewUrl}}
          onLoadStart={({nativeEvent}) => {
            currentWebViewUrl.current = nativeEvent.url;
            webPlayerIsFullscreen.current = false;
            console.info('Cinelode: page loading started');
            hideSplashScreen();
          }}
          onLoad={() => {
            hideSplashScreen();
          }}
          onError={({nativeEvent}) => {
            if (!isMainDocumentUrl(nativeEvent.url)) {
              console.warn(`Cinelode: ignoring resource error ${nativeEvent.code} for ${nativeEvent.url}`);
              return;
            }
            const description = nativeEvent.description
              ? ` ${nativeEvent.description}`
              : '';
            const url = nativeEvent.url ? ` URL: ${nativeEvent.url}` : '';
            fail(`Seite konnte nicht geladen werden (Fehler ${nativeEvent.code}).${description}${url}`);
          }}
          onHttpError={({nativeEvent}) => {
            console.error(`Cinelode: HTTP error ${nativeEvent.statusCode}`);
            if (nativeEvent.isMainFrame) {
              fail(`Seite konnte nicht geladen werden (HTTP ${nativeEvent.statusCode}).`);
            }
          }}
          onSslError={(_error, callback) => {
            callback.cancel();
            fail('TLS-Zertifikat konnte nicht überprüft werden.');
          }}
          onMessage={({nativeEvent}) => {
            try {
              const parsedMessage: unknown = JSON.parse(nativeEvent.data);
              if (
                typeof parsedMessage === 'object' &&
                parsedMessage !== null &&
                'type' in parsedMessage &&
                parsedMessage.type === 'cinelode-startup-loading' &&
                'loading' in parsedMessage &&
                typeof parsedMessage.loading === 'boolean'
              ) {
                const messageUrl = nativeEvent.url || currentWebViewUrl.current;
                if (!loadFailed.current && isFrontendPage(messageUrl)) {
                  setStatus(parsedMessage.loading ? 'loading' : 'ready');
                }
              } else if (
                typeof parsedMessage === 'object' &&
                parsedMessage !== null &&
                'type' in parsedMessage &&
                parsedMessage.type === 'cinelode-player-state' &&
                'isFullscreen' in parsedMessage &&
                typeof parsedMessage.isFullscreen === 'boolean'
              ) {
                webPlayerIsFullscreen.current = parsedMessage.isFullscreen;
              } else if (
                typeof parsedMessage === 'object' &&
                parsedMessage !== null &&
                'type' in parsedMessage &&
                parsedMessage.type === 'cinelode-open-external' &&
                'url' in parsedMessage &&
                typeof parsedMessage.url === 'string'
              ) {
                const messageUrl = nativeEvent.url || currentWebViewUrl.current;
                if (!isFrontendPage(messageUrl)) {
                  console.warn('Cinelode: external link request from untrusted page');
                  return;
                }
                if (!/^https?:\/\/(?:[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?|\[[0-9a-f:.]+\])(?::\d{1,5})?(?:[/?#][^\s\\]*)?$/i.test(parsedMessage.url)) {
                  externalOpenFailed();
                  return;
                }
                Linking.openURL(parsedMessage.url).catch(externalOpenFailed);
              } else if (
                typeof parsedMessage === 'object' &&
                parsedMessage !== null &&
                'type' in parsedMessage &&
                parsedMessage.type === 'cinelode-history' &&
                'canGoBack' in parsedMessage &&
                typeof parsedMessage.canGoBack === 'boolean'
              ) {
                webHistoryCanGoBack.current = parsedMessage.canGoBack;
              } else if (
                typeof parsedMessage === 'object' &&
                parsedMessage !== null &&
                'type' in parsedMessage &&
                parsedMessage.type === 'cinelode-exit-confirmed'
              ) {
                BackHandler.exitApp();
              }
            } catch {
              console.warn('Cinelode: ignoring malformed WebView message');
            }
          }}
        />
      )}
      {status !== 'ready' && (
        <View
          style={[
            styles.overlay,
            status === 'loading' && styles.loadingOverlay,
          ]}>
          {status === 'loading' ? (
            <Image
              accessible
              accessibilityLabel="Cinelode wird geladen ..."
              source={require('../assets/image/cinelode-loader.gif')}
              resizeMode="contain"
              style={styles.loader}
            />
          ) : (
            <Text style={styles.message}>{message}</Text>
          )}
          {status === 'error' && validUrl && (
            <Pressable
              hasTVPreferredFocus
              onPress={() => {
                webHistoryCanGoBack.current = false;
                loadFailed.current = false;
                setStatus('loading');
                setAttempt(value => value + 1);
              }}
              style={styles.retry}>
              <Text style={styles.message}>Erneut versuchen</Text>
            </Pressable>
          )}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#101011'},
  webview: {flex: 1, backgroundColor: '#101011'},
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#101011',
  },
  loadingOverlay: {backgroundColor: '#000000'},
  loader: {width: '80%', height: '80%', maxWidth: 414, maxHeight: 414},
  message: {color: '#ffffff', fontSize: 22, textAlign: 'center'},
  retry: {marginTop: 24, padding: 16, backgroundColor: '#333333'},
});
