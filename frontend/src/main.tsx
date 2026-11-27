import {StrictMode} from 'react';
import ReactDOM from 'react-dom/client';
import {App} from './app/App';
import {AuthProvider} from './features/auth/AuthProvider';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
    <StrictMode><AuthProvider><App/></AuthProvider></StrictMode>,
);
