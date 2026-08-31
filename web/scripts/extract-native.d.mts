import type {Plan} from '../src/domain/types';
export function parseThemes(source:string):Record<string,Record<string,string>>;
export function parseTemplates(source:string):Plan[];
export function nativeLogoSvg(source:string):string;
