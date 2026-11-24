import * as React from 'react';
import {fireEvent, render} from '@testing-library/react-native';
import config from '../config.json';
import {App} from '../src/App';
import {isValidFrontendUrl} from '../src/config';

const mockGoBack = jest.fn();
const mockInjectJavaScript = jest.fn();
const mockHideSplash = jest.fn();
const mockRemove = jest.fn();
const mockAddBackListener = jest.fn();
const mockOpenURL = jest.fn();

jest.mock('../config.json', () => ({
  frontendUrl: 'https://cinelode.example:5173',
}));

jest.mock('@amazon-devices/webview', () => {
  const react = jest.requireActual<typeof import('react')>('react');
  return {
    WebView: react.forwardRef<{
      goBack: () => void;
      injectJavaScript: (script: string) => void;
    }, {testID?: string}>(
      (props, ref) => {
        react.useImperativeHandle(ref, () => ({
          goBack: mockGoBack,
          injectJavaScript: mockInjectJavaScript,
        }));
        return react.createElement('WebView', props);
      },
    ),
  };
});

jest.mock('@amazon-devices/react-native-kepler', () => ({
  usePreventHideSplashScreen: jest.fn(),
  useHideSplashScreenCallback: () => mockHideSplash,
  Animated: {
    Value: class {
      setValue = jest.fn();
      stopAnimation = jest.fn();
    },
    timing: (
      value: {setValue: (next: number) => void},
      animationConfig: {toValue: number},
    ) => ({
      start: (callback?: (result: {finished: boolean}) => void) => {
        value.setValue(animationConfig.toValue);
        callback?.({finished: true});
      },
    }),
    View: 'Animated.View',
  },
  Linking: {openURL: (...args: unknown[]) => mockOpenURL(...args)},
  BackHandler: {
    addEventListener: (event: string, handler: () => boolean) =>
      mockAddBackListener(event, handler),
  },
  StyleSheet: {
    create: (styles: unknown) => styles,
    absoluteFillObject: {},
    flatten: (style: unknown) => style,
  },
  View: 'View',
  Text: 'Text',
  Pressable: 'Pressable',
}));

describe('frontend URL', () => {
  it.each([
    ['https://cinelode.example:5173', true],
    ['https://192.0.2.1/', true],
    ['', false],
    ['http://cinelode.example', false],
    ['https://user:password@cinelode.example', false],
    ['https://cinelode.example/path', false],
    ['https://cinelode.example:65536', false],
    ['http://10.0.0.5:5173', true], 
    ['http://172.20.0.5:5173', true],
  ])('validates %s', (url, expected) => {
    expect(isValidFrontendUrl(url)).toBe(expected);
  });
});

describe('App', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAddBackListener.mockReturnValue({remove: mockRemove});
    config.frontendUrl = 'https://cinelode.example:5173';
  });

  it('displays a configuration error and hides the splash screen', () => {
    config.frontendUrl = '';
    const screen = render(<App />);
    expect(screen.getByText(/Frontend-URL/)).toBeTruthy();
    expect(screen.queryByTestId('cinelode-webview')).toBeNull();
    expect(mockHideSplash).toHaveBeenCalled();
  });

  it('shows loading and forwards root back after the WebView finishes loading', () => {
    const screen = render(<App />);
    const webview = screen.getByTestId('cinelode-webview');
    expect(webview.props.source.uri).toMatch(
      /^https:\/\/cinelode\.example:5173\?cinelodeLaunch=\d+$/,
    );
    expect(screen.getByText('Cinelode wird geladen ...')).toBeTruthy();
    const back = mockAddBackListener.mock.calls[0][1] as () => boolean;
    expect(back()).toBe(true);
    expect(mockInjectJavaScript).toHaveBeenCalled();
    fireEvent(webview, 'loadStart', {nativeEvent: {canGoBack: false}});
    expect(mockHideSplash).toHaveBeenCalled();
    fireEvent(webview, 'load', {nativeEvent: {canGoBack: true}});
    expect(screen.queryByText('Cinelode wird geladen ...')).toBeNull();
    expect(mockHideSplash).toHaveBeenCalled();
    expect(back()).toBe(true);
    expect(mockGoBack).not.toHaveBeenCalled();
    expect(mockInjectJavaScript).toHaveBeenLastCalledWith(
      expect.stringContaining("key: 'BrowserBack'"),
    );
    screen.unmount();
    expect(mockRemove).toHaveBeenCalled();
  });

  it('forwards terminal back presses into the WebView', () => {
    render(<App />);
    const back = mockAddBackListener.mock.calls[0][1] as () => boolean;

    expect(back()).toBe(true);
    expect(mockInjectJavaScript).toHaveBeenCalledWith(
      expect.stringContaining("key: 'BrowserBack'"),
    );
  });

  it('uses the frontend history bridge for same-document navigation', () => {
    const screen = render(<App />);
    const webview = screen.getByTestId('cinelode-webview');
    const back = mockAddBackListener.mock.calls[0][1] as () => boolean;

    fireEvent(webview, 'message', {
      nativeEvent: {
        data: JSON.stringify({type: 'cinelode-history', canGoBack: true}),
      },
    });

    expect(back()).toBe(true);
    expect(mockGoBack).toHaveBeenCalledTimes(1);
  });

    it('passes trusted HTTP links to the device', () => {
      mockOpenURL.mockResolvedValue(undefined);
      const screen = render(<App />);
      fireEvent(screen.getByTestId('cinelode-webview'), 'message', {
        nativeEvent: {
          url: 'https://cinelode.example:5173/',
          data: JSON.stringify({
            type: 'cinelode-open-external',
            url: 'https://example.com/watch',
          }),
        },
      });
      expect(mockOpenURL).toHaveBeenCalledWith('https://example.com/watch');
    });

    it('uses the current trusted page when the message event has no URL', () => {
      mockOpenURL.mockResolvedValue(undefined);
      const screen = render(<App />);
      fireEvent(screen.getByTestId('cinelode-webview'), 'message', {
        nativeEvent: {
          data: JSON.stringify({
            type: 'cinelode-open-external',
            url: 'https://example.com/watch',
          }),
        },
      });
      expect(mockOpenURL).toHaveBeenCalledWith(
        'https://example.com/watch',
      );
    });

    it('passes Amazon Video detail links to the device unchanged', () => {
      mockOpenURL.mockResolvedValue(undefined);
      const screen = render(<App />);
      fireEvent(screen.getByTestId('cinelode-webview'), 'message', {
        nativeEvent: {
          data: JSON.stringify({
            type: 'cinelode-open-external',
            url: 'https://www.amazon.de/gp/video/detail/B0H8PGRYZ9',
          }),
        },
      });
      expect(mockOpenURL).toHaveBeenCalledWith(
        'https://www.amazon.de/gp/video/detail/B0H8PGRYZ9',
      );
    });

    it('rejects a message without a URL after navigating away from the frontend', () => {
      const screen = render(<App />);
      const webview = screen.getByTestId('cinelode-webview');
      fireEvent(webview, 'loadStart', {
        nativeEvent: {url: 'https://attacker.example/page'},
      });
      fireEvent(webview, 'message', {
        nativeEvent: {
          data: JSON.stringify({
            type: 'cinelode-open-external',
            url: 'https://www.amazon.de/gp/video/detail/B0H8PGRYZ9',
          }),
        },
      });
      expect(mockOpenURL).not.toHaveBeenCalled();
    });

    it('rejects external requests from untrusted pages and unsafe schemes', () => {
      const screen = render(<App />);
      const webview = screen.getByTestId('cinelode-webview');
      fireEvent(webview, 'message', {
        nativeEvent: {
          url: 'https://cinelode.example:5173.attacker.example/',
          data: JSON.stringify({type: 'cinelode-open-external', url: 'https://example.com'}),
        },
      });
      fireEvent(webview, 'message', {
        nativeEvent: {
          url: 'https://cinelode.example:5173/',
          data: JSON.stringify({type: 'cinelode-open-external', url: 'data:text/plain,unsafe'}),
        },
      });
      expect(mockOpenURL).not.toHaveBeenCalled();
      expect(mockInjectJavaScript).toHaveBeenCalledWith(
        expect.stringContaining('cinelode-external-open-error'),
      );
    });

    it('reports a device failure to the frontend', async () => {
      mockOpenURL.mockRejectedValue(new Error('No handler'));
      const screen = render(<App />);
      fireEvent(screen.getByTestId('cinelode-webview'), 'message', {
        nativeEvent: {
          url: 'https://cinelode.example:5173/',
          data: JSON.stringify({type: 'cinelode-open-external', url: 'https://example.com'}),
        },
      });
      await Promise.resolve();
      expect(mockInjectJavaScript).toHaveBeenCalledWith(
        expect.stringContaining('cinelode-external-open-error'),
      );
    });

  it('rejects TLS errors, keeps the error visible and permits retry', () => {
    const screen = render(<App />);
    const webview = screen.getByTestId('cinelode-webview');
    const cancel = jest.fn();
    fireEvent(webview, 'sslError', {}, {cancel});
    expect(cancel).toHaveBeenCalled();
    expect(mockHideSplash).toHaveBeenCalled();
    fireEvent(webview, 'load', {nativeEvent: {canGoBack: false}});
    expect(screen.getByText(/TLS-Zertifikat/)).toBeTruthy();
    fireEvent.press(screen.getByText('Erneut versuchen'));
    expect(screen.getByText('Cinelode wird geladen ...')).toBeTruthy();
  });

  it('only treats HTTP errors in the main frame as page failures', () => {
    const screen = render(<App />);
    const webview = screen.getByTestId('cinelode-webview');
    fireEvent(webview, 'httpError', {
      nativeEvent: {statusCode: 404, isMainFrame: false},
    });
    expect(screen.queryByText(/HTTP 404/)).toBeNull();
    fireEvent(webview, 'httpError', {
      nativeEvent: {statusCode: 503, isMainFrame: true},
    });
    expect(screen.getByText(/HTTP 503/)).toBeTruthy();
    expect(mockHideSplash).toHaveBeenCalled();
  });

  it('reports network errors', () => {
    const screen = render(<App />);
    fireEvent(screen.getByTestId('cinelode-webview'), 'error', {
      nativeEvent: {code: -2, url: 'https://cinelode.example:5173/'},
    });
    expect(screen.getByText(/Fehler -2/)).toBeTruthy();
    expect(mockHideSplash).toHaveBeenCalled();
  });

  it('does not replace the page for a failed media resource', () => {
    const screen = render(<App />);
    fireEvent(screen.getByTestId('cinelode-webview'), 'error', {
      nativeEvent: {
        code: -2,
        url: 'https://cinelode.example:5173/api/movies/46/play?preview=true&cycle=4',
      },
    });
    expect(screen.queryByText(/Fehler -2/)).toBeNull();
  });
});
