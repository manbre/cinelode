import {WebView} from '@amazon-devices/webview';
import {
  useHideSplashScreenCallback,
  usePreventHideSplashScreen,
  Linking,
} from '@amazon-devices/react-native-kepler';
import * as React from 'react';
import {useEffect, useRef, useState} from 'react';
import {
  Animated,
  BackHandler,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import config from '../config.json';
import {isValidFrontendUrl} from './config';

export const App = () => {
  const webRef = useRef<React.ElementRef<typeof WebView>>(null);
  const nativeCanGoBack = useRef(false);
  const webHistoryCanGoBack = useRef(false);
  const webPlayerIsFullscreen = useRef(false);
  const loadFailed = useRef(false);
  const hasLoadedOnce = useRef(false);
  const loadingOverlayTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const webViewUrl = useRef(
    `${config.frontendUrl}?cinelodeLaunch=${Date.now()}`,
  ).current;
  const validUrl = isValidFrontendUrl(config.frontendUrl);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>(
    validUrl ? 'loading' : 'error',
  );
  const loadingOverlayOpacity = useRef(new Animated.Value(1)).current;
  const loadingOverlayFadeStarted = useRef(false);
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
      if (
        (nativeCanGoBack.current || webHistoryCanGoBack.current) &&
        webRef.current
      ) {
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

  useEffect(() => () => {
    if (loadingOverlayTimer.current) {
      clearTimeout(loadingOverlayTimer.current);
    }
    loadingOverlayOpacity.stopAnimation();
  }, [loadingOverlayOpacity]);

  const fail = (reason: string) => {
    loadFailed.current = true;
    loadingOverlayOpacity.stopAnimation();
    loadingOverlayOpacity.setValue(1);
    loadingOverlayFadeStarted.current = false;
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

  const fadeLoadingOverlay = () => {
    if (loadingOverlayFadeStarted.current) {
      return;
    }
    loadingOverlayFadeStarted.current = true;
    Animated.timing(loadingOverlayOpacity, {
      toValue: 0,
      duration: 450,
      useNativeDriver: true,
    }).start(({finished}) => {
      if (finished) {
        setStatus('ready');
      }
    });
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
            nativeCanGoBack.current = nativeEvent.canGoBack;
            webPlayerIsFullscreen.current = false;
            loadFailed.current = false;
            if (loadingOverlayTimer.current) {
              clearTimeout(loadingOverlayTimer.current);
            }
            if (!hasLoadedOnce.current) {
              loadingOverlayTimer.current = setTimeout(() => {
                if (!loadFailed.current) {
                  hasLoadedOnce.current = true;
                  fadeLoadingOverlay();
                }
                loadingOverlayTimer.current = null;
              }, 5000);
            }
            if (!hasLoadedOnce.current) {
              setStatus('loading');
            }
            console.info('Cinelode: page loading started');
            hideSplashScreen();
          }}
          onLoad={({nativeEvent}) => {
            nativeCanGoBack.current = nativeEvent.canGoBack;
            hasLoadedOnce.current = true;
            if (loadingOverlayTimer.current) {
              clearTimeout(loadingOverlayTimer.current);
              loadingOverlayTimer.current = null;
            }
            if (!loadFailed.current) {
              console.info('Cinelode: page loading completed');
              fadeLoadingOverlay();
            }
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
                if (!isFrontendPage(nativeEvent.url)) {
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
        <Animated.View style={[styles.overlay, {opacity: loadingOverlayOpacity}]}>
          <Text style={styles.message}>
            {status === 'loading' ? 'Cinelode wird geladen ...' : message}
          </Text>
          {status === 'error' && validUrl && (
            <Pressable
              hasTVPreferredFocus
              onPress={() => {
                nativeCanGoBack.current = false;
                webHistoryCanGoBack.current = false;
                loadFailed.current = false;
                loadingOverlayOpacity.stopAnimation();
                loadingOverlayOpacity.setValue(1);
                loadingOverlayFadeStarted.current = false;
                setStatus('loading');
                setAttempt(value => value + 1);
              }}
              style={styles.retry}>
              <Text style={styles.message}>Erneut versuchen</Text>
            </Pressable>
          )}
        </Animated.View>
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
  message: {color: '#ffffff', fontSize: 22, textAlign: 'center'},
  retry: {marginTop: 24, padding: 16, backgroundColor: '#333333'},
});
